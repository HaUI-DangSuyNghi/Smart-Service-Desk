package vn.haui.dx.hpdi.data;
import java.time.Instant;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.data.api.*;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.data.internal.CoreKnowledgeCatalog;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreKnowledgeCatalogTest {
    @Test void authorizationPrecedesAnyProvenanceOrContentRead() {
        var human=mock(HumanContext.class); var metadata=mock(MetadataService.class); var files=mock(FileService.class);
        when(human.currentActor("lab-a")).thenThrow(CapabilityException.forbidden("Denied"));
        assertThrows(CapabilityException.class, () -> new CoreKnowledgeCatalog(human,metadata,files).resolve("lab-a",UUID.randomUUID()));
        verifyNoInteractions(metadata,files);
    }
    @Test void resolvesOnlyExactAuthorizedBytesAndReturnsDefensiveCopies() throws Exception {
        var human=mock(HumanContext.class); var metadata=mock(MetadataService.class); var files=mock(FileService.class);
        var id=UUID.randomUUID(); var file=UUID.randomUUID(); byte[] bytes="Shared knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        when(metadata.get("lab-a",id)).thenReturn(new MetadataRecord(id,"lab-a","Guide","src:guide","1","CC0",checksum,file,"owner",Instant.now()));
        when(files.describe("lab-a",file)).thenReturn(new StoredFile(file,"lab-a","guide.txt","text/plain",bytes.length,checksum,"owner",Instant.now()));
        when(files.open("lab-a",file)).thenReturn(new ByteArrayInputStream(bytes));
        var catalog=new CoreKnowledgeCatalog(human,metadata,files);
        var resolved=catalog.resolve("lab-a",id);
        assertArrayEquals(bytes,resolved.content());
        resolved.content()[0]=0;
        assertArrayEquals(bytes,resolved.content());
        when(files.open("lab-a",file)).thenReturn(new ByteArrayInputStream("changed".getBytes()));
        assertThrows(CapabilityException.class,()->catalog.resolve("lab-a",id));
        when(files.describe("lab-a",file)).thenReturn(new StoredFile(file,"lab-b","guide.txt","text/plain",bytes.length,checksum,"owner",Instant.now()));
        assertThrows(CapabilityException.class,()->catalog.resolve("lab-a",id));
    }
}
