package com.kebabshop.backend.menu;

import com.jayway.jsonpath.JsonPath;
import com.kebabshop.backend.auth.AdminAccountRepository;
import com.kebabshop.backend.auth.AdminProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuApiTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_menu_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }

    @Autowired MockMvc mvc;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    @Autowired MenuCategoryRepository categories;
    @Autowired MenuItemRepository items;

    @BeforeEach
    void reset() {
        items.deleteAll();
        categories.deleteAll();
        accounts.deleteAll();
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
    }

    private MockHttpSession ownerSession() throws Exception {
        var result = mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private String category(String name, int order, boolean active) {
        return "{\"name\":\"" + name + "\",\"displayOrder\":" + order + ",\"active\":" + active + "}";
    }

    private String item(long categoryId, String name, String price, boolean active,
                        boolean available, int order) {
        return "{\"categoryId\":" + categoryId + ",\"name\":\"" + name
                + "\",\"description\":\"Fresh food\",\"priceEur\":" + price
                + ",\"active\":" + active + ",\"available\":" + available
                + ",\"displayOrder\":" + order + "}";
    }

    private long createCategory(MockHttpSession session, String name, int order, boolean active) throws Exception {
        var result = mvc.perform(post("/api/admin/menu/categories").session(session).with(csrf())
                        .contentType("application/json").content(category(name, order, active)))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createItem(MockHttpSession session, long categoryId, String name, String price,
                            boolean active, boolean available, int order) throws Exception {
        var result = mvc.perform(post("/api/admin/menu/items").session(session).with(csrf())
                        .contentType("application/json")
                        .content(item(categoryId, name, price, active, available, order)))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void publicMenuShowsOnlyVisibleContentInStableOrderAndRetainsSoldOutItems() throws Exception {
        MockHttpSession session = ownerSession();
        long later = createCategory(session, "Later", 2, true);
        long first = createCategory(session, "First", 1, true);
        long hidden = createCategory(session, "Hidden", 0, false);
        createCategory(session, "Empty", 0, true);
        createItem(session, later, "Later item", "9.50", true, true, 0);
        createItem(session, first, "Sold out", "8.00", true, false, 0);
        createItem(session, first, "Available", "7.25", true, true, 0);
        createItem(session, first, "Draft", "6.00", false, true, 0);
        createItem(session, hidden, "Hidden item", "5.00", true, true, 0);

        mvc.perform(get("/api/public/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].name").value("First"))
                .andExpect(jsonPath("$.categories[0].items.length()").value(2))
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Sold out"))
                .andExpect(jsonPath("$.categories[0].items[0].available").value(false))
                .andExpect(jsonPath("$.categories[0].items[1].name").value("Available"))
                .andExpect(jsonPath("$.categories[1].name").value("Later"));
        mvc.perform(get("/api/admin/menu/items").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(post("/api/public/menu").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void categoryAndItemCrudProtectRelationshipsAndValidatePrices() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(post("/api/admin/menu/categories").with(csrf()).contentType("application/json")
                        .content(category("Food", 0, true)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/menu/categories").session(session).contentType("application/json")
                        .content(category("Food", 0, true)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/menu/categories").session(session).with(csrf())
                        .contentType("application/json").content(category(" ", -1, true)))
                .andExpect(status().isBadRequest());
        long food = createCategory(session, "Food", 0, true);
        long drinks = createCategory(session, "Drinks", 1, true);
        mvc.perform(put("/api/admin/menu/categories/" + food).session(session).with(csrf())
                        .contentType("application/json").content(category("Hot food", 2, true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Hot food"));
        mvc.perform(post("/api/admin/menu/items").session(session).with(csrf())
                        .contentType("application/json").content(item(food, "Kebab", "0", true, true, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/menu/items").session(session).with(csrf())
                        .contentType("application/json").content(item(food, "Kebab", "1.999", true, true, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/menu/items").session(session).with(csrf())
                        .contentType("application/json").content(item(99999, "Kebab", "8.50", true, true, 0)))
                .andExpect(status().isNotFound());
        long kebab = createItem(session, food, "Kebab", "8.50", true, true, 0);
        mvc.perform(delete("/api/admin/menu/categories/" + food).session(session).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/menu/items/" + kebab).session(session).with(csrf())
                        .contentType("application/json")
                        .content(item(drinks, "Drink", "2.20", true, false, 3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(drinks));
        mvc.perform(delete("/api/admin/menu/categories/" + food).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/menu/categories/" + drinks).session(session).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/menu/items/" + kebab).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/menu/items/" + kebab).session(session).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/menu/categories/" + drinks).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(0, items.count());
        assertEquals(0, categories.count());
    }

    @Test
    void rejectedWritesAndMalformedBodiesDoNotMutateMenu() throws Exception {
        MockHttpSession session = ownerSession();
        long categoryId = createCategory(session, "Food", 0, true);
        long itemId = createItem(session, categoryId, "Kebab", "8.50", true, true, 0);
        mvc.perform(put("/api/admin/menu/items/" + itemId).session(session)
                        .contentType("application/json")
                        .content(item(categoryId, "Bad", "8.50", true, true, 0)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/menu/items/" + itemId).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/menu/items/" + itemId).session(session).with(csrf())
                        .contentType("application/json")
                        .content(item(categoryId, "Bad", "-1", true, true, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/menu/items").session(session).with(csrf())
                        .contentType("application/json").content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid menu data"));
        mvc.perform(get("/api/admin/menu/items/" + itemId).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Kebab"));
        mvc.perform(get("/api/admin/menu/categories"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/public/menu").with(csrf()).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
