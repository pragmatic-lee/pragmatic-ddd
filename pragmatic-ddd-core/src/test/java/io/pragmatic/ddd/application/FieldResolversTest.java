package io.pragmatic.ddd.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 EntityPropertyCalculator / EntityPropertyResolver 契约与 EntityPropertyResolvers#from 适配器的行为。
 */
class FieldResolversTest {

    @Test
    void from_adaptsExtractorAndCalculator() {
        EntityPropertyCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        EntityPropertyResolver<String, String, String> resolver = EntityPropertyResolvers.from(
                String.class, String.class, calculator, (command, entity) -> command);

        assertThat(resolver.resolve("x", "y")).isEqualTo("x:y");
    }

    @Test
    void resolve_commandOnly_delegatesWithNullEntity() {
        EntityPropertyCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        EntityPropertyResolver<String, String, String> resolver = EntityPropertyResolvers.from(
                String.class, String.class, calculator, (command, entity) -> command);

        assertThat(resolver.resolve("x")).isEqualTo("x:null");
    }

    @Test
    void fieldResolver_directLambda_resolvesCommandAndEntity() {
        EntityPropertyResolver<String, String, String> resolver =
                (command, entity) -> command + "->" + entity;

        assertThat(resolver.resolve("cmd", "ent")).isEqualTo("cmd->ent");
        assertThat(resolver.resolve("cmd")).isEqualTo("cmd->null");
    }
}
