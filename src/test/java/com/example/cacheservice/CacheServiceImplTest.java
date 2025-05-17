package com.example.cacheservice;

import com.example.cacheservice.entity.MyEntity;
import com.example.cacheservice.exception.CacheException;
import com.example.cacheservice.repository.MyEntityRepository;
import com.example.cacheservice.service.CacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CacheServiceImplTest {

    @Mock
    private MyEntityRepository repository;

    @InjectMocks
    private CacheServiceImpl cacheService;

    private MyEntity entityWithoutId;
    private MyEntity entityWithId;

    @BeforeEach
    void setup() {
        // By default maxSize is set by @Value("${cache.maxSize:5}")
        // If needed, you can explicitly construct a new CacheServiceImpl with a given maxSize:
        // cacheService = new CacheServiceImpl(repository, 5);

        // Entity without ID
        entityWithoutId = new MyEntity();
        entityWithoutId.setName("NoID");

        // Entity with ID
        entityWithId = new MyEntity();
        entityWithId.setId(1L);
        entityWithId.setName("WithID");
    }

    @Test
    void testAddEntityWithoutId() throws CacheException {
        MyEntity savedEntity = new MyEntity();
        savedEntity.setId(100L);
        savedEntity.setName("GeneratedID");

        when(repository.save(entityWithoutId)).thenReturn(savedEntity);

        cacheService.add(entityWithoutId);

        verify(repository).save(entityWithoutId);

        // Should now be in cache
        MyEntity retrieved = cacheService.get(savedEntity);
        assertNotNull(retrieved);
        assertEquals(100L, retrieved.getId());
        assertEquals("GeneratedID", retrieved.getName());
    }

    @Test
    void testAddEntityWithId() throws CacheException {
        when(repository.save(entityWithId)).thenReturn(entityWithId);

        cacheService.add(entityWithId);

        verify(repository).save(entityWithId);

        MyEntity retrieved = cacheService.get(entityWithId);
        assertNotNull(retrieved);
        assertEquals(entityWithId.getId(), retrieved.getId());
        assertEquals("WithID", retrieved.getName());
    }

    @Test
    void testGetEntityFromCache() throws CacheException {
        when(repository.save(entityWithId)).thenReturn(entityWithId);
        cacheService.add(entityWithId);

        // Should be retrieved from cache now
        MyEntity retrieved = cacheService.get(entityWithId);
        assertEquals(entityWithId.getId(), retrieved.getId());

        // Repo should not be queried again since it's in cache
        verify(repository, never()).findById(entityWithId.getId());
    }

    @Test
    void testGetEntityFromDbWhenNotInCache() throws CacheException {
        when(repository.findById(entityWithId.getId())).thenReturn(Optional.of(entityWithId));

        // Not added to cache first, so should fetch from DB
        MyEntity retrieved = cacheService.get(entityWithId);
        assertEquals(entityWithId.getId(), retrieved.getId());

        // Now it should be cached. Subsequent get should not invoke DB.
        reset(repository);
        MyEntity retrievedAgain = cacheService.get(entityWithId);
        assertEquals(entityWithId.getId(), retrievedAgain.getId());
        verify(repository, never()).findById(entityWithId.getId());
    }

    @Test
    void testGetNonExistingEntityThrowsException() {
        MyEntity nonExisting = new MyEntity();
        nonExisting.setId(999L);

        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(CacheException.class, () -> cacheService.get(nonExisting));
    }

    @Test
    void testRemoveEntity() throws CacheException {
        when(repository.save(entityWithId)).thenReturn(entityWithId);
        when(repository.existsById(entityWithId.getId())).thenReturn(true);

        cacheService.add(entityWithId);
        cacheService.remove(entityWithId);

        verify(repository).deleteById(entityWithId.getId());

        // Now getting should fail
        assertThrows(CacheException.class, () -> cacheService.get(entityWithId));
    }

    @Test
    void testRemoveEntityWithoutIdThrowsException() {
        assertThrows(CacheException.class, () -> cacheService.remove(entityWithoutId));
    }

    @Test
    void testRemoveAll() throws CacheException {
        when(repository.save(entityWithId)).thenReturn(entityWithId);
        cacheService.add(entityWithId);

        cacheService.removeAll();
        verify(repository).deleteAll();

        assertThrows(CacheException.class, () -> cacheService.get(entityWithId));
    }

    @Test
    void testClear() throws CacheException {
        when(repository.save(entityWithId)).thenReturn(entityWithId);
        cacheService.add(entityWithId);

        cacheService.clear();

        // Cache is cleared, DB untouched. On get, it should fetch from DB
        when(repository.findById(entityWithId.getId())).thenReturn(Optional.of(entityWithId));
        MyEntity retrieved = cacheService.get(entityWithId);
        assertNotNull(retrieved);
    }

    @Test
    void testCacheEviction() throws CacheException {
        // Assume maxSize=5. Adding 6 entities should evict the oldest (id=1)
        when(repository.save(any(MyEntity.class))).thenAnswer(invocation -> {
            MyEntity entity = invocation.getArgument(0);
            // Just return the entity with its ID as is, simulating a DB that doesn't change IDs.
            return entity;
        });

        for (long i = 1; i <= 6; i++) {
            MyEntity ent = new MyEntity();
            ent.setId(i);
            ent.setName("Entity " + i);
            cacheService.add(ent);
        }

        // The first entity (id=1) should be evicted to DB.
        // Try to get it back:
        MyEntity firstEntity = new MyEntity();
        firstEntity.setId(1L);

        // Not in cache now, should try DB
        when(repository.findById(1L)).thenReturn(Optional.of(firstEntity));
        MyEntity retrieved = cacheService.get(firstEntity);
        assertNotNull(retrieved);
    }

    @Test
    void testGetEntityWithoutIdThrowsException() {
        assertThrows(CacheException.class, () -> cacheService.get(entityWithoutId));
    }

    @Test
    void testAddNullEntityThrowsException() {
        assertThrows(CacheException.class, () -> cacheService.add(null));
    }
}
