package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.IEntityPropertyCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IEntityPropertyCalculator 契约与 EntityPropertyResolvers#of 适配器的行为。
  * @author wizard-lee
 */
class EntityPropertyResolversTest {

    @Test
    void of_biFunction_extractor_resolvesCommandAndEntity() {
        IEntityPropertyCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        IEntityPropertyResolver<String, String, String> resolver = EntityPropertyResolvers.of(
                calculator, (command, entity) -> command);

        assertThat(resolver.resolve("x", "y")).isEqualTo("x:y");
    }

    @Test
    void of_function_extractor_resolvesCommandOnly() {
        IEntityPropertyCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        IEntityPropertyResolver<String, String, String> resolver = EntityPropertyResolvers.of(
                calculator, command -> command);

        assertThat(resolver.resolve("x", "y")).isEqualTo("x:y");
    }

    @Test
    void resolve_commandOnly_delegatesWithNullEntity() {
        IEntityPropertyCalculator<String, String, String> calculator =
                (source, entity) -> source + ":" + entity;
        IEntityPropertyResolver<String, String, String> resolver = EntityPropertyResolvers.of(
                calculator, (command, entity) -> command);

        assertThat(resolver.resolve("x")).isEqualTo("x:null");
    }

    @Test
    void entityPropertyResolver_directLambda_resolvesCommandAndEntity() {
        IEntityPropertyResolver<String, String, String> resolver =
                (command, entity) -> command + "->" + entity;

        assertThat(resolver.resolve("cmd", "ent")).isEqualTo("cmd->ent");
        assertThat(resolver.resolve("cmd")).isEqualTo("cmd->null");
    }
}
