package com.github.rpc0.kryo;

import com.esotericsoftware.kryo.util.MapReferenceResolver;
import com.esotericsoftware.kryo.util.Util;

import java.util.Comparator;

/**
 * @author fishzhao
 * @since 2022-01-21
 */
public final class TrustedReferenceResolver extends MapReferenceResolver {


  public TrustedReferenceResolver() {
  }

  public TrustedReferenceResolver(int maximumCapacity) {
    super(maximumCapacity);
  }


  @Override
  public boolean useReferences(Class type) {
    return !KryoFactory.isValueOrValueArrayType(type)
           && !Util.isEnum(type)
           && !Comparator.class.isAssignableFrom(type);
  }
}
