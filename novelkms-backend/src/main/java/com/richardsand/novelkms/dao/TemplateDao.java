package com.richardsand.novelkms.dao;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.dbcp2.BasicDataSource;
import com.richardsand.novelkms.model.Template;

/** Template cascade: BOOK -> USER -> SYSTEM. */
public class TemplateDao {
    public static final String TYPE_COVER="COVER", TYPE_PART="PART";
    public static final String SCOPE_SYSTEM="SYSTEM", SCOPE_USER="USER", SCOPE_BOOK="BOOK";
    public static final String DEFAULT_COVER_CONTENT="""
        <h1 style="text-align: center"><span data-token="TITLE"></span></h1>
        <p style="text-align: center"><span data-token="SUBTITLE"></span></p>
        <p style="text-align: center">By <span data-token="AUTHOR_FULL_NAME"></span></p>
        """;
    public static final String DEFAULT_PART_CONTENT="""
        <h1 style="text-align: center">Part <span data-token="PART_NUMBER"></span></h1>
        <h2 style="text-align: center"><span data-token="PART_TITLE"></span></h2>
        """;
    public static String defaultContentFor(String type){ return TYPE_PART.equals(type)?DEFAULT_PART_CONTENT:DEFAULT_COVER_CONTENT; }

    private final BasicDataSource ds;
    public TemplateDao(BasicDataSource ds){this.ds=ds;}
    private Template map(ResultSet rs)throws SQLException{return Template.builder().id(rs.getObject("id",UUID.class)).templateType(rs.getString("template_type")).scope(rs.getString("scope")).bookId(rs.getObject("book_id",UUID.class)).content(rs.getString("content")).createdAt(rs.getTimestamp("created_at").toInstant()).updatedAt(rs.getTimestamp("updated_at").toInstant()).build();}
    private Optional<Template> one(String sql,Object... args)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);try(ResultSet rs=ps.executeQuery()){return rs.next()?Optional.of(map(rs)):Optional.empty();}}}

    public Optional<Template> findSystem(String type)throws SQLException{return one("SELECT * FROM template WHERE scope='SYSTEM' AND template_type=? AND book_id IS NULL AND user_id IS NULL",type);}
    public Template getOrCreateSystem(String type)throws SQLException{Optional<Template> x=findSystem(type); if(x.isPresent())return x.get(); UUID id=UUID.randomUUID();Instant now=Instant.now();try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO template(id,template_type,scope,book_id,user_id,content,created_at,updated_at) VALUES (?,?,'SYSTEM',NULL,NULL,?,?,?)")){ps.setObject(1,id);ps.setString(2,type);ps.setString(3,defaultContentFor(type));ps.setTimestamp(4,Timestamp.from(now));ps.setTimestamp(5,Timestamp.from(now));ps.executeUpdate();}return findSystem(type).orElseThrow();}

    public Optional<Template> findUser(UUID userId,String type)throws SQLException{return one("SELECT * FROM template WHERE scope='USER' AND user_id=? AND template_type=? AND book_id IS NULL",userId,type);}
    public Template resolveForUser(UUID userId,String type)throws SQLException{return findUser(userId,type).orElseGet(()->{try{return getOrCreateSystem(type);}catch(SQLException e){throw new RuntimeException(e);}});}
    public Template upsertUser(UUID userId,String type,String content)throws SQLException{Optional<Template>x=findUser(userId,type);Instant now=Instant.now();if(x.isPresent()){try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE template SET content=?,updated_at=? WHERE id=?")){ps.setString(1,content);ps.setTimestamp(2,Timestamp.from(now));ps.setObject(3,x.get().getId());ps.executeUpdate();}}else{try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO template(id,template_type,scope,book_id,user_id,content,created_at,updated_at) VALUES (?,?,'USER',NULL,?,?,?,?)")){ps.setObject(1,UUID.randomUUID());ps.setString(2,type);ps.setObject(3,userId);ps.setString(4,content);ps.setTimestamp(5,Timestamp.from(now));ps.setTimestamp(6,Timestamp.from(now));ps.executeUpdate();}}return findUser(userId,type).orElseThrow();}
    public boolean deleteUser(UUID userId,String type)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM template WHERE scope='USER' AND user_id=? AND template_type=?")){ps.setObject(1,userId);ps.setString(2,type);return ps.executeUpdate()>0;}}
    public Template resetUser(UUID userId,String type)throws SQLException{deleteUser(userId,type);return resolveForUser(userId,type);}

    public Optional<Template> findBookOverride(UUID bookId,String type)throws SQLException{return one("SELECT * FROM template WHERE scope='BOOK' AND book_id=? AND template_type=?",bookId,type);}
    public Template resolveForBook(UUID userId,UUID bookId,String type)throws SQLException{Optional<Template>x=findBookOverride(bookId,type);return x.isPresent()?x.get():resolveForUser(userId,type);}
    /** Compatibility overload used by ExportService; derives the owner from the book. */
    public Template resolveForBook(UUID bookId,String type)throws SQLException{
        UUID userId;
        try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("SELECT p.owner_user_id FROM book b JOIN project p ON p.id=b.project_id WHERE b.id=?")){
            ps.setObject(1,bookId); try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Book not found");userId=rs.getObject(1,UUID.class);}
        }
        return resolveForBook(userId,bookId,type);
    }
    public Template upsertBookOverride(UUID bookId,String type,String content)throws SQLException{Optional<Template>x=findBookOverride(bookId,type);Instant now=Instant.now();if(x.isPresent()){try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("UPDATE template SET content=?,updated_at=? WHERE id=?")){ps.setString(1,content);ps.setTimestamp(2,Timestamp.from(now));ps.setObject(3,x.get().getId());ps.executeUpdate();}}else{try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("INSERT INTO template(id,template_type,scope,book_id,user_id,content,created_at,updated_at) VALUES (?,?,'BOOK',?,NULL,?,?,?)")){ps.setObject(1,UUID.randomUUID());ps.setString(2,type);ps.setObject(3,bookId);ps.setString(4,content);ps.setTimestamp(5,Timestamp.from(now));ps.setTimestamp(6,Timestamp.from(now));ps.executeUpdate();}}return findBookOverride(bookId,type).orElseThrow();}
    public boolean deleteBookOverride(UUID bookId,String type)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("DELETE FROM template WHERE scope='BOOK' AND book_id=? AND template_type=?")){ps.setObject(1,bookId);ps.setString(2,type);return ps.executeUpdate()>0;}}
}
