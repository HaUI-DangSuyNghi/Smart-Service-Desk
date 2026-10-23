package vn.haui.dx.hpdi.data;
import java.time.Instant;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import vn.haui.dx.core.data.api.*;
import vn.haui.dx.hpdi.human.api.HumanContext;
import vn.haui.dx.hpdi.human.api.MemberDirectory;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.data.internal.CoreKnowledgeCatalog;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreKnowledgeCatalogTest {
    @Test void authorizationPrecedesAnyProvenanceOrContentRead() {
        var human=mock(HumanContext.class); var metadata=mock(MetadataService.class); var files=mock(FileService.class);
        when(human.currentActor("lab-a")).thenThrow(CapabilityException.forbidden("Denied"));
        assertThrows(CapabilityException.class, () -> new CoreKnowledgeCatalog(human,metadata,files,mock(MemberDirectory.class)).resolve("lab-a",UUID.randomUUID()));
        verifyNoInteractions(metadata,files);
    }
    @Test void resolvesOnlyExactAuthorizedBytesAndReturnsDefensiveCopies() throws Exception {
        var human=mock(HumanContext.class); var metadata=mock(MetadataService.class); var files=mock(FileService.class);
        var id=UUID.randomUUID(); var file=UUID.randomUUID(); byte[] bytes="Shared knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String checksum=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        when(metadata.get("lab-a",id)).thenReturn(new MetadataRecord(id,"lab-a","Guide","src:guide","1","CC0",checksum,file,"owner",Instant.now()));
        when(files.describe("lab-a",file)).thenReturn(new StoredFile(file,"lab-a","guide.txt","text/plain",bytes.length,checksum,"owner",Instant.now()));
        when(files.open("lab-a",file)).thenReturn(new ByteArrayInputStream(bytes));
        var catalog=new CoreKnowledgeCatalog(human,metadata,files,mock(MemberDirectory.class));
        var resolved=catalog.resolve("lab-a",id);
        assertArrayEquals(bytes,resolved.content());
        assertEquals("Shared knowledge",resolved.text());
        resolved.content()[0]=0;
        assertArrayEquals(bytes,resolved.content());
        when(files.open("lab-a",file)).thenReturn(new ByteArrayInputStream("changed".getBytes()));
        assertThrows(CapabilityException.class,()->catalog.resolve("lab-a",id));
        when(files.describe("lab-a",file)).thenReturn(new StoredFile(file,"lab-b","guide.txt","text/plain",bytes.length,checksum,"owner",Instant.now()));
        assertThrows(CapabilityException.class,()->catalog.resolve("lab-a",id));
    }
    @Test void searchListsOnlyTextKnowledgeWithOwnerNames() {
        var human=mock(HumanContext.class); var metadata=mock(MetadataService.class); var files=mock(FileService.class);
        var directory=mock(MemberDirectory.class);
        var id=UUID.randomUUID(); var created=Instant.parse("2026-10-24T00:00:00Z");
        var record=new MetadataRecord(id,"lab-a","Wi-Fi","src:wifi","1","CC0","0".repeat(64),UUID.randomUUID(),"sub-2",created);
        when(metadata.search(eq("lab-a"),any(MetadataQuery.class),any())).thenReturn(
            new vn.haui.dx.core.common.Page<>(java.util.List.of(record),0,20,1));
        when(directory.displayNames("lab-a")).thenReturn(java.util.Map.of("sub-2","Demo Agent"));
        var page=new CoreKnowledgeCatalog(human,metadata,files,directory)
            .search("lab-a","  wi  ",vn.haui.dx.hpdi.common.api.PageRequest.of(null,null));
        verify(human).currentActor("lab-a");
        var query=org.mockito.ArgumentCaptor.forClass(MetadataQuery.class);
        verify(metadata).search(eq("lab-a"),query.capture(),any());
        assertEquals("wi",query.getValue().q());
        assertEquals(java.util.Set.of("text/plain","text/markdown"),query.getValue().fileMediaTypes());
        assertEquals(1,page.total());
        assertEquals("Demo Agent",page.items().get(0).ownerName());
        verifyNoInteractions(files);
    }
    @Test void searchAuthorizesBeforeReadingMetadata() {
        var human=mock(HumanContext.class); var metadata=mock(MetadataService.class); var directory=mock(MemberDirectory.class);
        when(human.currentActor("lab-a")).thenThrow(CapabilityException.forbidden("Denied"));
        assertThrows(CapabilityException.class,()->new CoreKnowledgeCatalog(human,metadata,mock(FileService.class),directory)
            .search("lab-a",null,vn.haui.dx.hpdi.common.api.PageRequest.of(null,null)));
        verifyNoInteractions(metadata,directory);
    }
}
