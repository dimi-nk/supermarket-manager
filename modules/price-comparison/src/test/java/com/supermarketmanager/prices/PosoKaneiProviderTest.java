package com.supermarketmanager.prices;

import com.sun.net.httpserver.HttpServer;
import com.supermarketmanager.core.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Request shape and field mapping against a local stub of the (undocumented) PosoKanei API. */
class PosoKaneiProviderTest {
    HttpServer server;
    final List<String> bodies = new ArrayList<>();

    PosoKaneiProvider stub(int status, String... pages) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/products/search", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = pages[Math.min(bodies.size() - 1, pages.length - 1)].getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("content-type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        return new PosoKaneiProvider("http://127.0.0.1:" + server.getAddress().getPort(), 0, 5000);
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void mapsProductsAndFollowsPages() throws Exception {
        String page1 = """
                {"products":[{"id":42,"name":"ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ 1KG","brand":"ΔΕΛΤΑ","gtin":"520","unit":"kg","unit_quantity":1,
                  "private_label":false,"retailer_prices":[
                    {"retailer":"ab","retailer_display_name":"AB","price":4.2,"price_normalized":4.2,"is_discount":true,"last_updated":"2026-10-12T06:00:00Z"},
                    {"retailer_name":"Lidl","price":3.9},
                    {"retailer":"x"}]}],
                 "has_next":true}""";
        String page2 = "{\"products\":[{\"id\":\"43\",\"name\":\"ΓΑΛΑ 1L\"}],\"has_next\":false}";
        List<Product> products = stub(200, page1, page2).search("γιαούρτι", 3);

        assertThat(bodies).hasSize(2);
        assertThat(bodies.getFirst()).contains("\"title\":\"γιαούρτι\"").contains("\"page\":1").contains("\"page_size\":50");
        assertThat(bodies.get(1)).contains("\"page\":2");
        Product p = products.getFirst();
        assertThat(p.id()).isEqualTo("42");
        assertThat(p.barcode()).isEqualTo("520");
        assertThat(p.offers()).hasSize(2);
        assertThat(p.offers().getFirst().discount()).isTrue();
        assertThat(p.offers().get(1).retailer()).isEqualTo("Lidl");
        assertThat(products.get(1).offers()).isEmpty();
    }

    @Test
    void explainsBlockedRequests() throws Exception {
        PosoKaneiProvider p = stub(403, "forbidden");
        assertThatThrownBy(() -> p.search("γάλα", 1)).hasMessageContaining("HTTP 403").hasMessageContaining("blocked");
    }
}
