package io.pragmatic.ddd.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 FieldCalculator / FieldResolver 契约与 FieldResolvers#from 适配器的行为。
 */
class FieldResolversTest {

    @Test
    void from_adaptsExtractorAndCalculator() {
        FieldCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        FieldResolver<String, String, String> resolver = FieldResolvers.from(
                String.class, String.class, calculator, (command, entity) -> command);

        assertThat(resolver.resolve("x", "y")).isEqualTo("x:y");
    }

    @Test
    void resolve_commandOnly_delegatesWithNullEntity() {
        FieldCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        FieldResolver<String, String, String> resolver = FieldResolvers.from(
                String.class, String.class, calculator, (command, entity) -> command);

        assertThat(resolver.resolve("x")).isEqualTo("x:null");
    }

    @Test
    void fieldResolver_directLambda_resolvesCommandAndEntity() {
        FieldResolver<String, String, String> resolver =
                (command, entity) -> command + "->" + entity;

        assertThat(resolver.resolve("cmd", "ent")).isEqualTo("cmd->ent");
        assertThat(resolver.resolve("cmd")).isEqualTo("cmd->null");
    }
}
