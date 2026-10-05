package tn.esprit.spring.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import tn.esprit.spring.entities.Role;
import tn.esprit.spring.entities.User;

@TestMethodOrder(OrderAnnotation.class)
class UserServiceImplTest {

    @Test
    @Order(1)
    void testUserCreation() {
        Role role = Role.values()[0];
        User u = new User(1L, "Maryem", "Othmani", new Date(), role);
        assertEquals(1L, u.getId());
        assertEquals("Maryem", u.getFirstName());
        assertEquals(role, u.getRole());
        assertNotNull(u.getDateNaissance());
    }

    @Test
    @Order(2)
    void testUserSetters() {
        User u = new User();
        u.setFirstName("Ali");
        u.setLastName("BenAmor");
        assertEquals("Ali", u.getFirstName());
        assertTrue(u.getLastName().startsWith("BenAmor"));
    }

    @Test
    @Order(3)
    void testUserToString() {
        User u = new User("Maryem", "Othmani", new Date(), Role.values()[0]);
        assertTrue(u.toString().contains("Maryem"));
    }
}
