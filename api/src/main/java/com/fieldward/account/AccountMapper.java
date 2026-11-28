package com.fieldward.account;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AccountMapper {
    String COLUMNS = "id, email, display_name, password_hash, role, enabled, session_version, created_at";

    @Select("SELECT " + COLUMNS + " FROM accounts WHERE email = #{email}")
    Account findByEmail(String email);

    @org.apache.ibatis.annotations.Options(flushCache = org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    @Select("SELECT " + COLUMNS + " FROM accounts WHERE id = #{id}")
    Account findById(long id);

    @Insert("""
            INSERT INTO accounts (email, display_name, password_hash, role)
            VALUES (#{email}, #{displayName}, #{passwordHash}, #{role})
            ON CONFLICT (email) DO NOTHING
            """)
    int insert(@Param("email") String email, @Param("displayName") String displayName,
               @Param("passwordHash") String passwordHash, @Param("role") Account.Role role);

    @Select("""
            SELECT id::text AS id, email, display_name, role, enabled, created_at FROM accounts
            WHERE (#{query} = '' OR
                   strpos(lower(email), lower(#{query})) > 0 OR
                   strpos(lower(display_name), lower(#{query})) > 0)
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<AccountView> list(@Param("query") String query, @Param("limit") int limit, @Param("offset") long offset);

    @Select(value = """
            UPDATE accounts SET enabled = #{enabled},
                session_version = session_version + CASE WHEN enabled <> #{enabled} THEN 1 ELSE 0 END
            WHERE id = #{id} AND role = 'STUDENT'
            RETURNING
            """ + COLUMNS, affectData = true)
    Account setStudentEnabled(@Param("id") long id, @Param("enabled") boolean enabled);
}
