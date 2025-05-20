package com.example.cacheservice;

import com.example.cacheservice.entity.MyEntity;
import com.example.cacheservice.exception.CacheException;
import com.example.cacheservice.repository.MyEntityRepository;
import com.example.cacheservice.service.CacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CacheServiceImplTest {

    @Mock
    private MyEntityRepository repository;

    private CacheServiceImpl cacheService;

    private MyEntity entityWithoutId;
    private MyEntity entityWithId;

    @BeforeEach
    void setup() {
        // Manually inject the dependencies to avoid InjectMocks constructor issue
        cacheService = new CacheServiceImpl(repository, 5, 60000L);

        entityWithoutId = new MyEntity();
        entityWithoutId.setName("NoID");

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
        MyEntity retrieved = cacheService.get(entityWithId);
        assertEquals(entityWithId.getId(), retrieved.getId());
        verify(repository, never()).findById(entityWithId.getId());
    }

    @Test
    void testGetEntityFromDbWhenNotInCache() throws CacheException {
        when(repository.findById(entityWithId.getId())).thenReturn(Optional.of(entityWithId));
        MyEntity retrieved = cacheService.get(entityWithId);
        assertEquals(entityWithId.getId(), retrieved.getId());
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
        when(repository.findById(entityWithId.getId())).thenReturn(Optional.of(entityWithId));
        MyEntity retrieved = cacheService.get(entityWithId);
        assertNotNull(retrieved);
    }

    @Test
    void testCacheEviction() throws CacheException {
        when(repository.save(any(MyEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (long i = 1; i <= 6; i++) {
            MyEntity ent = new MyEntity();
            ent.setId(i);
            ent.setName("Entity " + i);
            cacheService.add(ent);
        }

        MyEntity firstEntity = new MyEntity();
        firstEntity.setId(1L);
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
