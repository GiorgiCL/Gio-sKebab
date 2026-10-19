package com.kebabshop.backend.allergen;

import com.jayway.jsonpath.JsonPath;
import com.kebabshop.backend.auth.AdminAccountRepository;
import com.kebabshop.backend.auth.AdminProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AllergenApiTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_allergens;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    MockHttpSession session;
    long category;

    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM menu_item_allergen");
        jdbc.update("DELETE FROM lunch_menu_item_allergen");
        jdbc.update("DELETE FROM menu_item");
        jdbc.update("DELETE FROM lunch_menu_item");
        jdbc.update("DELETE FROM menu_category");
        accounts.deleteAll();
        provisioning.createFirstAccount("allergen-owner@example.test", "temporary-allergen-test-password");
        session = (MockHttpSession) mvc.perform(post("/api/admin/auth/login").with(csrf())
                .contentType("application/json").content("""
                {"email":"allergen-owner@example.test","password":"temporary-allergen-test-password"}
                """)).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        var response = mvc.perform(post("/api/admin/menu/categories").session(session).with(csrf())
                .contentType("application/json").content("""
                {"name":"Test category","active":true,"displayOrder":0}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        category = ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    String endpoint(boolean lunch) { return lunch ? "/api/admin/lunch-menu/items" : "/api/admin/menu/items"; }
    String table(boolean lunch) { return lunch ? "lunch_menu_item" : "menu_item"; }
    String body(boolean lunch, String info) {
        return "{\"name\":\"Owner verified dish\",\"description\":\"Original details\",\"priceEur\":7.50,"
                + "\"active\":true,\"available\":true,\"featured\":false,\"imageUrl\":null,\"displayOrder\":0,"
                + (lunch ? "\"dayOfWeek\":\"MONDAY\"" : "\"categoryId\":" + category)
                + (info == null ? "" : ",\"allergenInfo\":" + info) + "}";
    }
    String info(String status, String selection) {
        return "{\"status\":\"" + status + "\",\"allergens\":" + selection + "}";
    }
    long create(boolean lunch, String info) throws Exception {
        var response = mvc.perform(post(endpoint(lunch)).session(session).with(csrf()).contentType("application/json")
                .content(body(lunch, info))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void defaultsAndAllExplicitStatesPersistAcrossSeparateRequestsAndLocales(boolean lunch) throws Exception {
        long id = create(lunch, null);
        String endpoint = endpoint(lunch) + "/" + id;
        mvc.perform(get(endpoint).session(session)).andExpect(jsonPath("$.allergenInfo.status").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.allergenInfo.allergens").isEmpty());
        assertEquals("NOT_VERIFIED", jdbc.queryForObject("SELECT allergen_status FROM " + table(lunch) + " WHERE id=?", String.class, id));
        String[] all = Arrays.stream(Allergen.values()).map(Enum::name).toArray(String[]::new);
        String selection = Arrays.stream(all).map(code -> "\"" + code + "\"").collect(Collectors.joining(",", "[", "]"));
        mvc.perform(put(endpoint).session(session).with(csrf()).contentType("application/json")
                .content(body(lunch, info("VERIFIED_WITH_ALLERGENS", selection))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergenInfo.allergens", containsInAnyOrder(all)));
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(lunch) + "_allergen WHERE item_id=?", Integer.class, id));
        // Legacy content edits cannot silently erase verified information.
        mvc.perform(put(endpoint).session(session).with(csrf()).contentType("application/json")
                .content(body(lunch, null).replace("Original details", "Updated details"))).andExpect(status().isOk());
        mvc.perform(get(endpoint).session(session)).andExpect(jsonPath("$.description").value("Updated details"))
                .andExpect(jsonPath("$.allergenInfo.status").value("VERIFIED_WITH_ALLERGENS"))
                .andExpect(jsonPath("$.allergenInfo.allergens", containsInAnyOrder(all)));
        for (String locale : new String[]{"lt", "en", "ru", "ka"}) {
            String path = lunch ? "$.days[0].items[0]" : "$.categories[0].items[0]";
            mvc.perform(get((lunch ? "/api/public/lunch-menu" : "/api/public/menu") + "?lang=" + locale))
                    .andExpect(status().isOk()).andExpect(jsonPath(path + ".allergenInfo.status").value("VERIFIED_WITH_ALLERGENS"))
                    .andExpect(jsonPath(path + ".allergenInfo.allergens", containsInAnyOrder(all)));
        }
        String previous = mvc.perform(get(endpoint).session(session)).andReturn().getResponse().getContentAsString();
        mvc.perform(put(endpoint).session(session).with(csrf()).contentType("application/json")
                .content(body(lunch, info("VERIFIED_WITH_ALLERGENS", "[\"CELERY\"]")).replace("Original details", "Updated details")))
                .andExpect(status().isOk());
        String current = mvc.perform(get(endpoint).session(session))
                .andExpect(jsonPath("$.allergenInfo.allergens[0]").value("CELERY"))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals((String) JsonPath.read(previous, "$.updatedAt"),
                (String) JsonPath.read(current, "$.updatedAt"), "Collection-only edits update the item timestamp");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(lunch) + "_allergen WHERE item_id=?", Integer.class, id));
        for (String state : new String[]{"VERIFIED_NONE_IDENTIFIED", "NOT_VERIFIED"}) {
            mvc.perform(put(endpoint).session(session).with(csrf()).contentType("application/json")
                    .content(body(lunch, info(state, "[]")))).andExpect(status().isOk());
            mvc.perform(get(endpoint).session(session)).andExpect(jsonPath("$.allergenInfo.status").value(state))
                    .andExpect(jsonPath("$.allergenInfo.allergens").isEmpty());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(lunch) + "_allergen WHERE item_id=?", Integer.class, id));
        }
        // Explicit verification works on create as well as on update; deleting cleans the collection.
        long verified = create(lunch, info("VERIFIED_WITH_ALLERGENS", "[\"MILK\",\"EGGS\"]"));
        mvc.perform(delete(endpoint(lunch) + "/" + verified).session(session).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(lunch) + "_allergen WHERE item_id=?", Integer.class, verified));
        create(lunch, info("VERIFIED_NONE_IDENTIFIED", "[]"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsContradictoryUnknownAndIncompleteDataWithoutChangingSavedState(boolean lunch) throws Exception {
        long id = create(lunch, info("VERIFIED_WITH_ALLERGENS", "[\"MILK\"]"));
        for (String invalid : new String[]{
                info("VERIFIED_WITH_ALLERGENS", "[]"), info("NOT_VERIFIED", "[\"MILK\"]"),
                info("VERIFIED_NONE_IDENTIFIED", "[\"MILK\"]"), info("UNKNOWN", "[]"),
                info("VERIFIED_WITH_ALLERGENS", "[\"MADE_UP\"]"), info("VERIFIED_WITH_ALLERGENS", "[null]"),
                info("NOT_VERIFIED", "null"), "{}", "{\"allergens\":[]}", "{\"status\":\"NOT_VERIFIED\"}"}) {
            mvc.perform(put(endpoint(lunch) + "/" + id).session(session).with(csrf()).contentType("application/json")
                    .content(body(lunch, invalid))).andExpect(status().isBadRequest());
            mvc.perform(post(endpoint(lunch)).session(session).with(csrf()).contentType("application/json")
                    .content(body(lunch, invalid))).andExpect(status().isBadRequest());
        }
        mvc.perform(get(endpoint(lunch) + "/" + id).session(session))
                .andExpect(jsonPath("$.allergenInfo.status").value("VERIFIED_WITH_ALLERGENS"))
                .andExpect(jsonPath("$.allergenInfo.allergens[0]").value("MILK"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(lunch), Integer.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void existingAuthenticationAndCsrfProtectAllergenEdits(boolean lunch) throws Exception {
        long id = create(lunch, null);
        String body = body(lunch, info("VERIFIED_NONE_IDENTIFIED", "[]"));
        mvc.perform(put(endpoint(lunch) + "/" + id).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(endpoint(lunch) + "/" + id).session(session).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }
}
