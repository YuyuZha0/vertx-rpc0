package io.vertxrpc0.kryo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrustedReferenceResolverTest {

  private final TrustedReferenceResolver resolver = new TrustedReferenceResolver();

  @Test
  public void valueTypesDoNotUseReferences() {
    assertFalse(resolver.useReferences(String.class));
    assertFalse(resolver.useReferences(Integer.class));
    assertFalse(resolver.useReferences(BigDecimal.class));
    assertFalse(resolver.useReferences(OffsetDateTime.class));
  }

  @Test
  public void primitiveArrayTypesDoNotUseReferences() {
    assertFalse(resolver.useReferences(int[].class));
    assertFalse(resolver.useReferences(byte[].class));
  }

  @Test
  public void valueArrayTypesDoNotUseReferences() {
    assertFalse(resolver.useReferences(Integer[].class));
  }

  @Test
  public void enumsDoNotUseReferences() {
    assertFalse(resolver.useReferences(TimeUnit.class));
  }

  @Test
  public void comparatorsDoNotUseReferences() {
    assertFalse(resolver.useReferences(Comparator.class));
    assertFalse(resolver.useReferences(Comparator.naturalOrder().getClass()));
  }

  @Test
  public void pojoTypesUseReferences() {
    assertTrue(resolver.useReferences(ArrayList.class));
    assertTrue(resolver.useReferences(Object.class));
  }
}
