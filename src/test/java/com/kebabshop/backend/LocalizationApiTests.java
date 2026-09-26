package com.kebabshop.backend;

import com.kebabshop.backend.auth.AdminAccountRepository;
import com.kebabshop.backend.auth.AdminProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalizationApiTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_localization_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM menu_item");
        jdbc.update("DELETE FROM menu_category");
        jdbc.update("DELETE FROM promotion");
        jdbc.update("DELETE FROM restaurant_profile");
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

    @Test
    void publicContentResolvesEachFieldAndRejectsUnsupportedLocales() throws Exception {
        jdbc.update("INSERT INTO restaurant_profile (id, display_name, description, address, phone, google_maps_url, created_at, updated_at) "
                + "VALUES (1, 'Gio', 'Lithuanian description', 'Vilnius', '123', 'https://example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO restaurant_profile_translation VALUES (1, 'en', 'Gio English', NULL)");
        jdbc.update("INSERT INTO menu_category (name, display_order, active, created_at, updated_at) "
                + "VALUES ('Kebabai', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long category = jdbc.queryForObject("SELECT MAX(id) FROM menu_category", Long.class);
        jdbc.update("INSERT INTO menu_item (category_id, name, description, price_eur, active, available, featured, display_order, created_at, updated_at) "
                + "VALUES (?, 'Vistiena', 'Skanu', 8.50, TRUE, FALSE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", category);
        Long item = jdbc.queryForObject("SELECT MAX(id) FROM menu_item", Long.class);
        jdbc.update("INSERT INTO menu_category_translation VALUES (?, 'en', 'Kebabs')", category);
        jdbc.update("INSERT INTO menu_item_translation VALUES (?, 'en', 'Chicken', NULL)", item);
        jdbc.update("INSERT INTO menu_item_translation VALUES (?, 'ru', NULL, 'Vkusno')", item);
        jdbc.update("INSERT INTO promotion (title, description, active, display_order, created_at, updated_at) "
                + "VALUES ('Pasiūlymas', 'Nuolaida', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO promotion_translation (promotion_id, locale, title, description) "
                + "SELECT id, 'ka', 'Shethavazeba', NULL FROM promotion");

        mvc.perform(get("/api/public/restaurant")).andExpect(jsonPath("$.displayName").value("Gio"));
        mvc.perform(get("/api/public/restaurant?lang=en")).andExpect(jsonPath("$.displayName").value("Gio English"))
                .andExpect(jsonPath("$.description").value("Lithuanian description"))
                .andExpect(jsonPath("$.address").value("Vilnius"));
        mvc.perform(get("/api/public/menu?lang=lt")).andExpect(jsonPath("$.categories[0].items[0].name").value("Vistiena"));
        mvc.perform(get("/api/public/menu?lang=en")).andExpect(jsonPath("$.categories[0].name").value("Kebabs"))
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Chicken"))
                .andExpect(jsonPath("$.categories[0].items[0].description").value("Skanu"))
                .andExpect(jsonPath("$.categories[0].items[0].available").value(false))
                .andExpect(jsonPath("$.categories[0].items[0].featured").value(true));
        mvc.perform(get("/api/public/menu?lang=ru")).andExpect(jsonPath("$.categories[0].items[0].name").value("Vistiena"))
                .andExpect(jsonPath("$.categories[0].items[0].description").value("Vkusno"));
        mvc.perform(get("/api/public/menu?lang=ka")).andExpect(jsonPath("$.categories[0].name").value("Kebabai"));
        mvc.perform(get("/api/public/promotions?lang=ka")).andExpect(jsonPath("$.promotions[0].title").value("Shethavazeba"))
                .andExpect(jsonPath("$.promotions[0].description").value("Nuolaida"));
        mvc.perform(get("/api/public/promotions?lang=en")).andExpect(jsonPath("$.promotions[0].title").value("Pasiūlymas"));
        mvc.perform(get("/api/public/menu?lang=de")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/restaurant?lang=EN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/promotions?lang=")).andExpect(status().isBadRequest());
    }

    @Test
    void adminCanReplaceAndClearTranslationsWithoutChangingOtherFields() throws Exception {
        var session = owner();
        String category = "{\"name\":\"Kebabai\",\"displayOrder\":0,\"active\":true,"
                + "\"translations\":{\"en\":{\"name\":\"Kebabs\"},\"ru\":{\"name\":\"Kebaby\"}}}";
        var created = mvc.perform(post("/api/admin/menu/categories").session(session).with(csrf())
                        .contentType("application/json").content(category)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.translations.lt.name").value("Kebabai"))
                .andExpect(jsonPath("$.translations.en.name").value("Kebabs")).andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(put("/api/admin/menu/categories/" + id).session(session).with(csrf())
                        .contentType("application/json").content("{\"name\":\"Kebabai\",\"displayOrder\":0,\"active\":true,"
                                + "\"translations\":{\"en\":{\"name\":\"Kebabs updated\"}}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.translations.en.name").value("Kebabs updated"))
                .andExpect(jsonPath("$.translations.ru").doesNotExist());
        mvc.perform(put("/api/admin/menu/categories/" + id).session(session).with(csrf())
                        .contentType("application/json").content("{\"name\":\"Kebabai\",\"displayOrder\":0,\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.translations.en.name").value("Kebabs updated"));
        mvc.perform(put("/api/admin/menu/categories/" + id).session(session).with(csrf())
                        .contentType("application/json").content("{\"name\":\"Kebabai\",\"displayOrder\":0,\"active\":true,"
                                + "\"translations\":{\"de\":{\"name\":\"Kebab\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/menu/categories/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEditsProfileItemAndPromotionTranslations() throws Exception {
        var session = owner();
        String profile = "{\"displayName\":\"Gio\",\"description\":\"Lithuanian\",\"address\":\"Vilnius\","
                + "\"phone\":\"123\",\"googleMapsUrl\":\"https://example.com\","
                + "\"translations\":{\"en\":{\"displayName\":\"Gio EN\",\"description\":\"English\"}}}";
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf()).contentType("application/json")
                        .content(profile)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.translations.lt.description").value("Lithuanian"))
                .andExpect(jsonPath("$.translations.en.description").value("English"));
        mvc.perform(get("/api/public/restaurant?lang=en"))
                .andExpect(jsonPath("$.description").value("English"))
                .andExpect(jsonPath("$.translations").doesNotExist());

        jdbc.update("INSERT INTO menu_category (name, display_order, active, created_at, updated_at) "
                + "VALUES ('Kebabai', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long category = jdbc.queryForObject("SELECT MAX(id) FROM menu_category", Long.class);
        String item = "{\"categoryId\":" + category + ",\"name\":\"Kebabas\",\"description\":\"Skanu\","
                + "\"priceEur\":8.50,\"active\":true,\"available\":true,\"featured\":false,\"displayOrder\":0,"
                + "\"translations\":{\"en\":{\"name\":\"Kebab\",\"description\":\"Tasty\"}}}";
        var itemResult = mvc.perform(post("/api/admin/menu/items").session(session).with(csrf())
                        .contentType("application/json").content(item)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.translations.en.name").value("Kebab")).andReturn();
        long itemId = ((Number) com.jayway.jsonpath.JsonPath.read(itemResult.getResponse().getContentAsString(), "$.id")).longValue();
        String clearedItem = "{\"categoryId\":" + category + ",\"name\":\"Kebabas\",\"description\":\"Skanu\","
                + "\"priceEur\":8.50,\"active\":true,\"available\":true,\"featured\":false,\"displayOrder\":0,"
                + "\"translations\":{\"en\":{\"name\":\"Kebab\",\"description\":\" \"}}}";
        mvc.perform(put("/api/admin/menu/items/" + itemId).session(session).with(csrf())
                        .contentType("application/json").content(clearedItem)).andExpect(status().isOk())
                .andExpect(jsonPath("$.translations.en.description").doesNotExist());
        mvc.perform(get("/api/public/menu?lang=en"))
                .andExpect(jsonPath("$.categories[0].items[0].description").value("Skanu"));

        String promotion = "{\"title\":\"Pasiulymas\",\"description\":\"Nuolaida\",\"active\":true,"
                + "\"displayOrder\":0,\"translations\":{\"ka\":{\"title\":\"Offer\"}}}";
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf())
                        .contentType("application/json").content(promotion)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.translations.ka.title").value("Offer"));
        mvc.perform(get("/api/public/promotions?lang=ka"))
                .andExpect(jsonPath("$.promotions[0].title").value("Offer"))
                .andExpect(jsonPath("$.promotions[0].description").value("Nuolaida"));
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf())
                        .contentType("application/json").content("{\"title\":\"Bad\",\"active\":true,"
                                + "\"displayOrder\":0,\"translations\":{\"de\":{\"title\":\"Bad\"}}}"))
                .andExpect(status().isBadRequest());
    }
}
