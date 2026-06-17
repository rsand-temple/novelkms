package com.richardsand.novelkms.dao;
import java.sql.*;import java.time.Instant;import java.util.*;import org.apache.commons.dbcp2.BasicDataSource;import com.fasterxml.jackson.databind.ObjectMapper;import com.richardsand.novelkms.model.*;
/** Style cascade: BOOK -> PROJECT -> USER -> SYSTEM. */
public class UserStyleDao {
 private static final ObjectMapper M=new ObjectMapper(); private final BasicDataSource ds; public UserStyleDao(BasicDataSource ds){this.ds=ds;}
 private String json(StyleDefinition d)throws SQLException{try{return M.writeValueAsString(d);}catch(Exception e){throw new SQLException(e);}}
 private Style map(ResultSet r)throws SQLException{try{return Style.builder().id(r.getObject("id",UUID.class)).styleKey(r.getString("style_key")).scope(r.getString("scope")).projectId(r.getObject("project_id",UUID.class)).bookId(r.getObject("book_id",UUID.class)).definition(M.readValue(r.getString("definition"),StyleDefinition.class)).createdAt(r.getTimestamp("created_at").toInstant()).updatedAt(r.getTimestamp("updated_at").toInstant()).build();}catch(Exception e){throw new SQLException(e);}}
 private Optional<Style> one(String sql,Object...a)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}}
 private Style insert(String key,String scope,UUID user,UUID project,UUID book,StyleDefinition d)throws SQLException{UUID id=UUID.randomUUID();Instant n=Instant.now();try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO style(id,style_key,scope,project_id,book_id,user_id,definition,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)")){p.setObject(1,id);p.setString(2,key);p.setString(3,scope);p.setObject(4,project);p.setObject(5,book);p.setObject(6,user);p.setString(7,json(d));p.setTimestamp(8,Timestamp.from(n));p.setTimestamp(9,Timestamp.from(n));p.executeUpdate();}return one("SELECT * FROM style WHERE id=?",id).orElseThrow();}
 private Style update(UUID id,StyleDefinition d)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("UPDATE style SET definition=?,updated_at=? WHERE id=?")){p.setString(1,json(d));p.setTimestamp(2,Timestamp.from(Instant.now()));p.setObject(3,id);p.executeUpdate();}return one("SELECT * FROM style WHERE id=?",id).orElseThrow();}
 public Style system(String k)throws SQLException{Optional<Style>x=one("SELECT * FROM style WHERE scope='SYSTEM' AND style_key=? AND user_id IS NULL AND project_id IS NULL AND book_id IS NULL",k);return x.isPresent()?x.get():insert(k,"SYSTEM",null,null,null,StyleDefaults.defaultFor(k));}
 public Optional<Style> user(UUID u,String k)throws SQLException{return one("SELECT * FROM style WHERE scope='USER' AND user_id=? AND style_key=?",u,k);}
 public Style resolveUser(UUID u,String k)throws SQLException{return user(u,k).orElse(system(k));}
 public Style upsertUser(UUID u,String k,StyleDefinition d)throws SQLException{Optional<Style>x=user(u,k);return x.isPresent()?update(x.get().getId(),d):insert(k,"USER",u,null,null,d);}
 public boolean deleteUser(UUID u,String k)throws SQLException{return del("DELETE FROM style WHERE scope='USER' AND user_id=? AND style_key=?",u,k);}
 public List<Style> allUser(UUID u)throws SQLException{List<Style>o=new ArrayList<>();for(String k:StyleDefaults.STYLE_KEYS)o.add(resolveUser(u,k));return o;}
 public Optional<Style> project(UUID p,String k)throws SQLException{return one("SELECT * FROM style WHERE scope='PROJECT' AND project_id=? AND style_key=?",p,k);}
 public Style resolveProject(UUID u,UUID p,String k)throws SQLException{return project(p,k).orElse(resolveUser(u,k));}
 public Style upsertProject(UUID p,String k,StyleDefinition d)throws SQLException{Optional<Style>x=project(p,k);return x.isPresent()?update(x.get().getId(),d):insert(k,"PROJECT",null,p,null,d);}
 public boolean deleteProject(UUID p,String k)throws SQLException{return del("DELETE FROM style WHERE scope='PROJECT' AND project_id=? AND style_key=?",p,k);}
 public Optional<Style> book(UUID b,String k)throws SQLException{return one("SELECT * FROM style WHERE scope='BOOK' AND book_id=? AND style_key=?",b,k);}
 private UUID projectForBook(UUID b)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement("SELECT project_id FROM book WHERE id=?")){p.setObject(1,b);try(ResultSet r=p.executeQuery()){return r.next()?r.getObject(1,UUID.class):null;}}}
 public Style resolveBook(UUID u,UUID b,String k)throws SQLException{Optional<Style>x=book(b,k);if(x.isPresent())return x.get();UUID p=projectForBook(b);return p==null?resolveUser(u,k):resolveProject(u,p,k);}
 public Style upsertBook(UUID b,String k,StyleDefinition d)throws SQLException{Optional<Style>x=book(b,k);return x.isPresent()?update(x.get().getId(),d):insert(k,"BOOK",null,null,b,d);}
 public boolean deleteBook(UUID b,String k)throws SQLException{return del("DELETE FROM style WHERE scope='BOOK' AND book_id=? AND style_key=?",b,k);}
 public List<Style> allProject(UUID u,UUID p)throws SQLException{List<Style>o=new ArrayList<>();for(String k:StyleDefaults.STYLE_KEYS)o.add(resolveProject(u,p,k));return o;}
 public List<Style> allBook(UUID u,UUID b)throws SQLException{List<Style>o=new ArrayList<>();for(String k:StyleDefaults.STYLE_KEYS)o.add(resolveBook(u,b,k));return o;}
 private boolean del(String sql,Object...a)throws SQLException{try(Connection c=ds.getConnection();PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);return p.executeUpdate()>0;}}
}
