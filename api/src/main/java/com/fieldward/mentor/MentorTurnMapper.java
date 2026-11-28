package com.fieldward.mentor;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MentorTurnMapper {
    String STORED = """
            id, conversation_id, CAST(request_id AS text) AS request_id, student_message, mentor_reply,
            status, attempt, created_at, side_chat_id, sources::text AS sources
            """;
    String READABLE = """
            id, conversation_id, CAST(request_id AS text) AS request_id, student_message, mentor_reply,
            CASE WHEN status = 'STREAMING' AND lease_until <= CURRENT_TIMESTAMP
                 THEN 'INTERRUPTED' ELSE status END AS status, attempt, created_at, side_chat_id, sources::text AS sources
            """;
    String THREAD = " AND side_chat_id IS NOT DISTINCT FROM CAST(#{side} AS bigint) ";
    String SEARCH = "(setweight(mentor_search_vector(turn.student_message), 'A') || "
            + "setweight(mentor_search_vector(turn.mentor_reply), 'C'))";

    @Select("SELECT account_id FROM mentor_main_conversations WHERE id = #{conversationId}")
    Long accountForConversation(long conversationId);

    @Select("SELECT id FROM mentor_main_conversations WHERE account_id = #{accountId}")
    Long findConversation(long accountId);

    @Select("SELECT id FROM mentor_main_conversations WHERE account_id = #{accountId} FOR UPDATE")
    Long lockConversation(long accountId);

    @Select("""
            SELECT side.id::text AS id, side.title, session.assessment_id::text AS verification_id
            FROM mentor_side_chats side
            LEFT JOIN career_skill_sessions session ON session.conversation_id = side.conversation_id
                AND session.side_chat_id = side.id
            WHERE side.conversation_id = #{conversation} ORDER BY side.id DESC
            """)
    List<Side> sides(long conversation);

    @Select("""
            SELECT title FROM mentor_side_chats WHERE conversation_id = #{conversation} AND id = #{side}
            """)
    String findSide(@Param("conversation") long conversation, @Param("side") long side);

    @Insert("""
            INSERT INTO mentor_side_chats (conversation_id, title) VALUES (#{conversation}, #{title})
            ON CONFLICT (conversation_id, title) DO NOTHING
            """)
    void createSide(@Param("conversation") long conversation, @Param("title") String title);

    @Select("""
            SELECT side.id::text AS id, side.title, session.assessment_id::text AS verification_id
            FROM mentor_side_chats side
            LEFT JOIN career_skill_sessions session ON session.conversation_id = side.conversation_id
                AND session.side_chat_id = side.id
            WHERE side.conversation_id = #{conversation} AND side.title = #{title}
            """)
    Side sideByTitle(@Param("conversation") long conversation, @Param("title") String title);

    @Select(value = """
            INSERT INTO mentor_side_chats (conversation_id, title) VALUES (#{conversation}, #{title})
            RETURNING id
            """, affectData = true)
    long createDedicatedSide(@Param("conversation") long conversation, @Param("title") String title);

    @Select(value = """
            INSERT INTO mentor_turns (conversation_id, side_chat_id, request_id, student_message, mentor_reply, status, attempt)
            VALUES (#{conversation}, #{side}, gen_random_uuid(), #{message}, #{reply}, 'COMPLETED', 1)
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn logSideTurn(@Param("conversation") long conversation, @Param("side") long side,
                           @Param("message") String message, @Param("reply") String reply);

    @Select("SELECT " + READABLE + """
            FROM mentor_turns WHERE conversation_id = #{conversation}
            """ + THREAD + """
                AND id < COALESCE(CAST(#{before} AS bigint), 9223372036854775807)
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<StoredTurn> history(@Param("conversation") long conversation, @Param("side") Long side,
                            @Param("before") Long before, @Param("limit") int limit);

    @Select("SELECT " + STORED + """
            FROM mentor_turns WHERE conversation_id = #{conversation}
            """ + THREAD + """
                AND status = 'STREAMING'
                AND lease_until > CURRENT_TIMESTAMP
            """)
    StoredTurn active(@Param("conversation") long conversation, @Param("side") Long side);

    @Select("SELECT " + STORED + """
            FROM mentor_turns WHERE conversation_id = #{conversation}
            """ + THREAD + """
                AND request_id = CAST(#{requestId} AS uuid) FOR UPDATE
            """)
    StoredTurn find(@Param("conversation") long conversation, @Param("side") Long side, @Param("requestId") String requestId);

    @Select("SELECT " + READABLE + """
            FROM mentor_turns WHERE conversation_id = #{conversation}
            """ + THREAD + """
                AND request_id = CAST(#{requestId} AS uuid)
            """)
    StoredTurn observe(@Param("conversation") long conversation, @Param("side") Long side, @Param("requestId") String requestId);

    @Select(value = """
            UPDATE mentor_turns SET status = 'INTERRUPTED', lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE conversation_id = #{conversation}
            """ + THREAD + """
                AND status = 'STREAMING' AND lease_until <= CURRENT_TIMESTAMP
            RETURNING
            """ + STORED, affectData = true)
    List<StoredTurn> expire(@Param("conversation") long conversation, @Param("side") Long side);

    @Select(value = """
            INSERT INTO mentor_turns (conversation_id, side_chat_id, request_id, student_message, status, attempt, lease_until)
            VALUES (#{conversation}, #{side}, CAST(#{requestId} AS uuid), #{message}, 'STREAMING', 1,
                CURRENT_TIMESTAMP + make_interval(secs => #{leaseSeconds}))
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn start(@Param("conversation") long conversation, @Param("side") Long side, @Param("requestId") String requestId,
                     @Param("message") String message, @Param("leaseSeconds") int leaseSeconds);

    @Select(value = """
            INSERT INTO mentor_turns (conversation_id, request_id, student_message, mentor_reply, status, attempt, sources)
            VALUES (#{conversation}, gen_random_uuid(), NULL, #{opening}, 'COMPLETED', 1, CAST(#{sources} AS jsonb))
            RETURNING id
            """, affectData = true)
    long openLearning(@Param("conversation") long conversation, @Param("opening") String opening, @Param("sources") String sources);

    @Update("""
            UPDATE mentor_turns SET mentor_reply = #{opening}, sources = CAST(#{sources} AS jsonb), updated_at = CURRENT_TIMESTAMP
            WHERE id = (
                SELECT id FROM mentor_turns WHERE conversation_id = #{conversation} AND side_chat_id IS NULL
                ORDER BY id DESC LIMIT 1
            ) AND student_message IS NULL AND sources -> 0 ->> 'href' = #{previousSource}
            """)
    int reviseUnansweredOpening(@Param("conversation") long conversation, @Param("previousSource") String previousSource,
                                 @Param("opening") String opening, @Param("sources") String sources);

    @Select(value = """
            UPDATE mentor_turns SET status = 'STREAMING', attempt = attempt + 1, updated_at = CURRENT_TIMESTAMP,
                lease_until = CURRENT_TIMESTAMP + make_interval(secs => #{leaseSeconds})
            WHERE id = #{id} AND status = 'INTERRUPTED'
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn retry(@Param("id") long id, @Param("leaseSeconds") int leaseSeconds);

    @Select(value = """
            UPDATE mentor_turns SET status = 'COMPLETED', mentor_reply = #{reply}, sources = CAST(#{sources} AS jsonb),
                lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND attempt = #{attempt} AND status = 'STREAMING'
                AND lease_until > CURRENT_TIMESTAMP
            RETURNING
            """ + STORED, affectData = true)
    StoredTurn complete(@Param("id") long id, @Param("attempt") int attempt, @Param("reply") String reply,
                        @Param("sources") String sources);

    @Update("""
            UPDATE mentor_turns SET status = 'INTERRUPTED', lease_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND attempt = #{attempt} AND status = 'STREAMING'
            """)
    int interrupt(@Param("id") long id, @Param("attempt") int attempt);

    @Select("SELECT " + STORED + """
            FROM mentor_turns WHERE conversation_id = #{conversation}
            """ + THREAD + """
                AND id < #{before} AND status = 'COMPLETED'
            ORDER BY id DESC LIMIT 40
            """)
    List<StoredTurn> context(@Param("conversation") long conversation, @Param("side") Long side, @Param("before") long before);

    @Select("""
            WITH matches AS (
                SELECT turn.id, ts_rank(
            """ + SEARCH + """
                , CAST(#{query} AS tsquery), 32) AS relevance
                FROM mentor_turns turn
                WHERE turn.conversation_id = #{conversation} AND turn.id < #{before}
                  AND CAST(#{query} AS tsquery) @@
            """ + SEARCH + """
                ORDER BY relevance DESC, turn.id DESC LIMIT 20
            ), adjacent_threads AS (
                SELECT id, 0::real AS relevance FROM mentor_turns
                WHERE conversation_id = #{conversation} AND id < #{before}
                    AND side_chat_id IS DISTINCT FROM CAST(#{side} AS bigint)
                ORDER BY id DESC LIMIT 6
            ), candidates AS (
                SELECT id, MAX(relevance) AS relevance FROM (
                    SELECT * FROM matches UNION ALL SELECT * FROM adjacent_threads
                ) hits GROUP BY id
            )
            SELECT turn.id, turn.side_chat_id, side.title, turn.student_message, turn.mentor_reply, turn.created_at
            FROM candidates
            JOIN mentor_turns turn ON turn.id = candidates.id AND turn.conversation_id = #{conversation}
            LEFT JOIN mentor_side_chats side ON side.conversation_id = turn.conversation_id AND side.id = turn.side_chat_id
            ORDER BY candidates.relevance DESC, turn.id DESC
            """)
    List<Recall> recall(@Param("conversation") long conversation, @Param("side") Long side,
                        @Param("before") long before, @Param("query") String query);

    enum Status { STREAMING, COMPLETED, INTERRUPTED }

    record StoredTurn(long id, long conversationId, String requestId, String studentMessage, String mentorReply,
                      Status status, int attempt, OffsetDateTime createdAt, Long sideChatId, String sources) {}

    record TurnView(String id, String requestId, String studentMessage, String mentorReply,
                    Status status, OffsetDateTime createdAt, List<MentorMemory.Source> sources) {}

    record Side(String id, String title, String verificationId) {}

    record Recall(long id, Long sideChatId, String title, String studentMessage, String mentorReply,
                  OffsetDateTime createdAt) {}
}
