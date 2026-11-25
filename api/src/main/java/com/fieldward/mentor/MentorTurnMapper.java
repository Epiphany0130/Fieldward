package com.fieldward.mentor;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MentorTurnMapper {
    String STORED = """
            id, conversation_id, CAST(request_id AS text) AS request_id, student_message, mentor_reply,
            status, attempt, created_at
            """;
    String READABLE = """
            id, conversation_id, CAST(request_id AS text) AS request_id, student_message, mentor_reply,
            CASE WHEN status = 'STREAMING' AND lease_until <= CURRENT_TIMESTAMP
                 THEN 'INTERRUPTED' ELSE status END AS status, attempt, created_at
            """;

    @Select("SELECT id FROM mentor_main_conversations WHERE account_id = #{accountId}")
    Long findConversation(long accountId);

    @Select("SELECT id FROM mentor_main_conversations WHERE account_id = #{accountId} FOR UPDATE")
    Long lockConversation(long accountId);

    @Select("SELECT " + READABLE + """
            FROM mentor_turns WHERE conversation_id = #{conversation}
                AND id < COALESCE(CAST(#{before} AS bigint), 9223372036854775807)
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<StoredTurn> history(@Param("conversation") long conversation, @Param("before") Long before, @Param("limit") int limit);

    @Select("SELECT " + STORED + """
            FROM mentor_turns WHERE conversation_id = #{conversation} AND status = 'STREAMING'
                AND lease_until > CURRENT_TIMESTAMP
            """)
    StoredTurn active(long conversation);

    @Select("SELECT " + STORED + """
            FROM mentor_turns WHERE conversation_id = #{conversation} AND request_id = CAST(#{requestId} AS uuid) FOR UPDATE
            """)
    StoredTurn find(@Param("conversation") long conversation, @Param("requestId") String requestId);

    @Select("SELECT " + READABLE + """
            FROM mentor_turns WHERE conversation_id = #{conversation} AND request_id = CAST(#{requestId} AS uuid)
            """)
    StoredTurn observe(@Param("conversation") long conversation, @Param("requestId") String requestId);

    @Update("""
            UPDATE mentor_turns SET status = 'INTERRUPTED', lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE conversation_id = #{conversation} AND status = 'STREAMING' AND lease_until <= CURRENT_TIMESTAMP
            """)
    void expire(long conversation);

    @Select(value = """
            INSERT INTO mentor_turns (conversation_id, request_id, student_message, status, attempt, lease_until)
            VALUES (#{conversation}, CAST(#{requestId} AS uuid), #{message}, 'STREAMING', 1,
                CURRENT_TIMESTAMP + make_interval(secs => #{leaseSeconds}))
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn start(@Param("conversation") long conversation, @Param("requestId") String requestId,
                     @Param("message") String message, @Param("leaseSeconds") int leaseSeconds);

    @Select(value = """
            UPDATE mentor_turns SET status = 'STREAMING', attempt = attempt + 1, updated_at = CURRENT_TIMESTAMP,
                lease_until = CURRENT_TIMESTAMP + make_interval(secs => #{leaseSeconds})
            WHERE id = #{id} AND status = 'INTERRUPTED'
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn retry(@Param("id") long id, @Param("leaseSeconds") int leaseSeconds);

    @Select(value = """
            UPDATE mentor_turns SET status = 'COMPLETED', mentor_reply = #{reply},
                lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND attempt = #{attempt} AND status = 'STREAMING'
                AND lease_until > CURRENT_TIMESTAMP
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn complete(@Param("id") long id, @Param("attempt") int attempt, @Param("reply") String reply);

    @Update("""
            UPDATE mentor_turns SET status = 'INTERRUPTED', lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND attempt = #{attempt} AND status = 'STREAMING'
            """)
    void interrupt(@Param("id") long id, @Param("attempt") int attempt);

    @Select("SELECT " + STORED + """
            FROM mentor_turns WHERE conversation_id = #{conversation} AND id < #{before} AND status = 'COMPLETED'
            ORDER BY id DESC LIMIT 6
            """)
    List<StoredTurn> context(@Param("conversation") long conversation, @Param("before") long before);

    enum Status { STREAMING, COMPLETED, INTERRUPTED }

    record StoredTurn(long id, long conversationId, String requestId, String studentMessage, String mentorReply,
                      Status status, int attempt, OffsetDateTime createdAt) {
        TurnView view() {
            return new TurnView(Long.toString(id), requestId, studentMessage, mentorReply, status, createdAt);
        }

        TurnView interruptedView() {
            return new TurnView(Long.toString(id), requestId, studentMessage, null, Status.INTERRUPTED, createdAt);
        }
    }

    record TurnView(String id, String requestId, String studentMessage, String mentorReply,
                    Status status, OffsetDateTime createdAt) {}
}
