package com.moa.service;
import com.moa.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.util.*;
@Service
public class MediaService {
    private final Path root;private final JdbcTemplate db;private final PostService posts;
    public MediaService(@Value("${moa.upload-dir}") String root,JdbcTemplate db,PostService posts)throws IOException {this.root=Path.of(root).toAbsolutePath().normalize();Files.createDirectories(this.root);this.db=db;this.posts=posts;}
    @Transactional public void upload(long actor,long postId,List<MultipartFile> files)throws IOException {
        posts.owner(actor,postId);db.queryForObject("SELECT post_id FROM posts WHERE post_id=? FOR UPDATE",Long.class,postId);
        int count=db.queryForObject("SELECT COUNT(*) FROM post_media WHERE post_id=?",Integer.class,postId);
        if(files==null || files.isEmpty() || count+files.size()>4)throw ApiException.bad("이미지는 최대 4장까지 첨부할 수 있습니다.");
        int order=Optional.ofNullable(db.queryForObject("SELECT MAX(sort_order) FROM post_media WHERE post_id=?",Integer.class,postId)).orElse(0);
        List<Path> written=new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)for(Path f:written)try{Files.deleteIfExists(f);}catch(IOException ignored){}}});
        for(var file:files){
            if(file.isEmpty() || file.getSize()>5*1024*1024)throw ApiException.bad("이미지는 5MB 이하로 선택해 주세요.");
            String format;
            try(ImageInputStream input=ImageIO.createImageInputStream(file.getInputStream())){
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw ApiException.bad("JPG 또는 PNG 이미지만 첨부할 수 있습니다.");
                var reader=readers.next();try{reader.setInput(input);format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(!Set.of("jpeg","png").contains(format) || (long)reader.getWidth(0)*reader.getHeight(0)>25_000_000L)throw ApiException.bad("JPG/PNG, 최대 2,500만 화소 이미지를 사용해 주세요.");
                    if(reader.read(0)==null)throw ApiException.bad("손상된 이미지입니다.");
                }finally{reader.dispose();}
            }
            String key=UUID.randomUUID()+"."+(format.equals("jpeg")?"jpg":"png");Path target=root.resolve(key);written.add(target);file.transferTo(target);
            db.update("INSERT INTO post_media(post_id,storage_key,sort_order,mime_type,byte_size) VALUES(?,?,?,?,?)",postId,key,++order,"image/"+format,file.getSize());
        }
    }
    public record Media(Path path,String type) {}
    public Media read(long id,long mediaId) {
        posts.get(id,null);var row=db.queryForList("SELECT storage_key,mime_type FROM post_media WHERE media_id=? AND post_id=?",mediaId,id).stream().findFirst().orElseThrow(ApiException::missing);
        Path path=root.resolve((String)row.get("storage_key")).normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw ApiException.missing();return new Media(path,(String)row.get("mime_type"));
    }
    @Transactional public void delete(long actor,long postId,long mediaId) {
        posts.owner(actor,postId);db.queryForObject("SELECT post_id FROM posts WHERE post_id=? FOR UPDATE",Long.class,postId);var rows=db.queryForList("SELECT storage_key FROM post_media WHERE media_id=? AND post_id=?",mediaId,postId);if(rows.isEmpty())throw ApiException.missing();
        db.update("DELETE FROM post_media WHERE media_id=? AND post_id=?",mediaId,postId);
        // Re-number remaining images without colliding with the unique (post_id,sort_order) key.
        var ids=db.queryForList("SELECT media_id FROM post_media WHERE post_id=? ORDER BY sort_order",Long.class,postId);int order=1;for(long mid:ids)db.update("UPDATE post_media SET sort_order=? WHERE media_id=?",order++,mid);
        Path file=root.resolve((String)rows.getFirst().get("storage_key")).normalize();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){if(file.startsWith(root))try{Files.deleteIfExists(file);}catch(IOException ignored){}}});
    }
}
