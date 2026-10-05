package tn.esprit.spring.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.spring.entities.Role;
import tn.esprit.spring.entities.User;
import tn.esprit.spring.repository.UserRepository;

@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(MockitoExtension.class)
class UserServiceImplMockTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserServiceImpl userService;

    User user1;
    User user2;

    @BeforeEach
    void setUp() {
        Role role = Role.values()[0];
        user1 = new User(1L, "Maryem", "Othmani", new Date(), role);
        user2 = new User(2L, "Ali", "BenAmor", new Date(), role);
    }

    @Test
    @Order(1)
    void testRetrieveAllUsers() {
        when(userRepository.findAll()).thenReturn(Arrays.asList(user1, user2));
        List<User> users = userService.retrieveAllUsers();
        assertEquals(2, users.size());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @Order(2)
    void testAddUser() {
        when(userRepository.save(any(User.class))).thenReturn(user1);
        User saved = userService.addUser(user1);
        assertNotNull(saved);
        assertEquals("Maryem", saved.getFirstName());
        verify(userRepository, times(1)).save(user1);
    }

    @Test
    @Order(3)
    void testUpdateUser() {
        user1.setFirstName("Mariem");
        when(userRepository.save(any(User.class))).thenReturn(user1);
        User updated = userService.updateUser(user1);
        assertNotNull(updated);
        assertEquals("Mariem", updated.getFirstName());
    }

    @Test
    @Order(4)
    void testRetrieveUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        User found = userService.retrieveUser("1");
        assertNotNull(found);
        assertEquals(1L, found.getId());
    }

    @Test
    @Order(5)
    void testRetrieveUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertNull(userService.retrieveUser("99"));
    }

    @Test
    @Order(6)
    void testDeleteUser() {
        userService.deleteUser("1");
        verify(userRepository, times(1)).deleteById(1L);
    }
}
