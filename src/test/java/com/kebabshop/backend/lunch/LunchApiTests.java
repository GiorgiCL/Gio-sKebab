package com.kebabshop.backend.lunch;

import com.jayway.jsonpath.JsonPath;
import com.kebabshop.backend.auth.AdminAccountRepository;
import com.kebabshop.backend.auth.AdminProvisioningService;
import com.kebabshop.backend.image.ManagedImageStorage;
import com.kebabshop.backend.image.ManagedImageStorage.ImageKind;
import com.kebabshop.backend.image.ManagedImageStorage.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LunchApiTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_lunch_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired LunchMenuItemRepository items;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ManagedImageStorage imageStorage;

    @Test void lunchUploadReplaceAndRemovalKeepDatabaseAndStorageInSync() throws Exception {
        var session = owner();
        long id = create(session, lunch("MONDAY", "Lunch", 0, true, true, "https://images.example.com/owner.jpg"));
        when(imageStorage.upload(eq(ImageKind.LUNCH), any(), eq("jpg"))).thenReturn(
                new StoredImage("https://res.cloudinary.com/demo/first", "gios-kebab/lunch/first"),
                new StoredImage("https://res.cloudinary.com/demo/second", "gios-kebab/lunch/second"));
        var photo = new MockMultipartFile("file", "lunch.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        mvc.perform(multipart("/api/admin/lunch-menu/items/" + id + "/image").file(photo)
                .session(session).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://res.cloudinary.com/demo/first"));
        assertEquals("gios-kebab/lunch/first", jdbc.queryForObject(
                "SELECT image_public_id FROM lunch_menu_item WHERE id = ?", String.class, id));
        verify(imageStorage, never()).delete(ImageKind.LUNCH, "https://images.example.com/owner.jpg");

        mvc.perform(multipart("/api/admin/lunch-menu/items/" + id + "/image").file(photo)
                .session(session).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://res.cloudinary.com/demo/second"));
        assertEquals("gios-kebab/lunch/second", jdbc.queryForObject(
                "SELECT image_public_id FROM lunch_menu_item WHERE id = ?", String.class, id));
        verify(imageStorage).delete(ImageKind.LUNCH, "gios-kebab/lunch/first");

        mvc.perform(delete("/api/admin/lunch-menu/items/" + id + "/image")
                .session(session).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").doesNotExist());
        assertEquals(null, jdbc.queryForObject(
                "SELECT image_public_id FROM lunch_menu_item WHERE id = ?", String.class, id));
        verify(imageStorage).delete(ImageKind.LUNCH, "gios-kebab/lunch/second");
    }

    @Test void standardLunchUpdateAndDeleteCleanUpManagedImages() throws Exception {
        var session = owner();
        long changed = create(session, lunch("MONDAY", "Changed", 0, true, true, null));
        jdbc.update("UPDATE lunch_menu_item SET image_url = ?, image_public_id = ? WHERE id = ?",
                "https://res.cloudinary.com/demo/image/upload/old.jpg", "gios-kebab/lunch/old", changed);
        mvc.perform(put("/api/admin/lunch-menu/items/" + changed).session(session).with(csrf())
                .contentType("application/json")
                .content(lunch("MONDAY", "Changed", 0, true, true, "https://images.example.com/owner.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://images.example.com/owner.jpg"));
        assertEquals(null, jdbc.queryForObject("SELECT image_public_id FROM lunch_menu_item WHERE id = ?", String.class, changed));
        verify(imageStorage).delete(ImageKind.LUNCH, "gios-kebab/lunch/old");

        long deleted = create(session, lunch("TUESDAY", "Deleted", 0, true, true, null));
        jdbc.update("UPDATE lunch_menu_item SET image_url = ?, image_public_id = ? WHERE id = ?",
                "https://res.cloudinary.com/demo/image/upload/deleted.jpg", "gios-kebab/lunch/deleted", deleted);
        mvc.perform(delete("/api/admin/lunch-menu/items/" + deleted).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        verify(imageStorage).delete(ImageKind.LUNCH, "gios-kebab/lunch/deleted");
    }

    @BeforeEach void reset() {
        items.deleteAll();
        accounts.deleteAll();
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
    }

    private MockHttpSession owner() throws Exception {
        var result = mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json")
                .content("{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private String lunch(String day, String name, int order, boolean active, boolean available, String imageUrl) {
        return "{\"dayOfWeek\":\"" + day + "\",\"name\":\"" + name + "\",\"description\":null,\"priceEur\":6.50,"
                + "\"active\":" + active + ",\"available\":" + available + ",\"displayOrder\":" + order
                + ",\"imageUrl\":" + (imageUrl == null ? "null" : "\"" + imageUrl + "\"") + "}";
    }

    private long create(MockHttpSession session, String body) throws Exception {
        var result = mvc.perform(post("/api/admin/lunch-menu/items").session(session).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test void publicOrderingVisibilityAndLocalization() throws Exception {
        var session = owner();
        create(session, lunch("TUESDAY", "Later day", 0, true, true, null));
        create(session, lunch("MONDAY", "Later", 5, true, false, null));
        String base = lunch("MONDAY", "Pietūs", 1, true, true, "https://images.example.com/lunch.jpg");
        String translated = base.substring(0, base.length() - 1)
                + ",\"translations\":{\"en\":{\"name\":\"Lunch\",\"description\":\"Fresh\"},"
                + "\"ru\":{\"name\":\"Обед\"},\"ka\":{\"description\":\"სადილი\"}}}";
        create(session, translated);
        create(session, lunch("MONDAY", "Hidden", 0, false, true, null));
        mvc.perform(get("/api/public/lunch-menu?lang=en")).andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.days[0].items.length()").value(2))
                .andExpect(jsonPath("$.days[0].items[0].name").value("Lunch"))
                .andExpect(jsonPath("$.days[0].items[0].description").value("Fresh"))
                .andExpect(jsonPath("$.days[0].items[0].imageUrl").value("https://images.example.com/lunch.jpg"))
                .andExpect(jsonPath("$.days[0].items[1].name").value("Later"))
                .andExpect(jsonPath("$.days[0].items[1].available").value(false))
                .andExpect(jsonPath("$.days[1].items[0].name").value("Later day"));
        mvc.perform(get("/api/public/lunch-menu?lang=ru")).andExpect(jsonPath("$.days[0].items[0].name").value("Обед"))
                .andExpect(jsonPath("$.days[0].items[0].description").doesNotExist());
        mvc.perform(get("/api/public/lunch-menu?lang=ka")).andExpect(jsonPath("$.days[0].items[0].name").value("Pietūs"))
                .andExpect(jsonPath("$.days[0].items[0].description").value("სადილი"));
        mvc.perform(get("/api/public/lunch-menu")).andExpect(jsonPath("$.days[0].items[0].name").value("Pietūs"));
        mvc.perform(get("/api/public/lunch-menu?lang=de")).andExpect(status().isBadRequest());
    }

    @Test void protectedCrudValidationAndCascade() throws Exception {
        mvc.perform(post("/api/admin/lunch-menu/items").with(csrf()).contentType("application/json")
                .content(lunch("MONDAY", "Food", 0, true, true, null))).andExpect(status().isUnauthorized());
        var session = owner();
        mvc.perform(post("/api/admin/lunch-menu/items").session(session).contentType("application/json")
                .content(lunch("MONDAY", "Food", 0, true, true, null))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/lunch-menu/items").session(session).with(csrf()).contentType("application/json")
                .content(lunch("FUNDAY", "Food", 0, true, true, null))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/lunch-menu/items").session(session).with(csrf()).contentType("application/json")
                .content(lunch("MONDAY", "Food", 0, true, true, "javascript:alert(1)"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/lunch-menu/items").session(session).with(csrf()).contentType("application/json")
                .content(lunch("MONDAY", "Food", -1, true, true, null))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/lunch-menu/items").session(session).with(csrf()).contentType("application/json")
                .content(lunch("MONDAY", "Food", 0, true, true, null).replace("\"priceEur\":6.50", "\"priceEur\":0")))
                .andExpect(status().isBadRequest());
        String canonical = lunch("MONDAY", "Food", 0, true, true, null);
        mvc.perform(post("/api/admin/lunch-menu/items").session(session).with(csrf()).contentType("application/json")
                .content(canonical.substring(0, canonical.length() - 1) + ",\"translations\":{\"lt\":{\"name\":\"Wrong\"}}}"))
                .andExpect(status().isBadRequest());
        long id = create(session, lunch("MONDAY", "Food", 0, true, true, null));
        mvc.perform(get("/api/admin/lunch-menu/items/" + id).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.lt.name").value("Food"));
        mvc.perform(put("/api/admin/lunch-menu/items/" + id).session(session).with(csrf())
                .contentType("application/json").content(lunch("FRIDAY", "New food", 2, true, true, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dayOfWeek").value("FRIDAY"));
        mvc.perform(get("/api/admin/lunch-menu/items").session(session)).andExpect(jsonPath("$.length()").value(1));
        jdbc.update("INSERT INTO lunch_menu_item_translation (item_id, locale, name) VALUES (?, 'en', 'New food')", id);
        mvc.perform(put("/api/admin/lunch-menu/items/" + id).session(session).with(csrf())
                .contentType("application/json").content(lunch("FRIDAY", "New food", 2, true, true, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.translations.en.name").value("New food"));
        mvc.perform(delete("/api/admin/lunch-menu/items/" + id).session(session).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lunch_menu_item_translation WHERE item_id = ?", Integer.class, id));
    }
}
