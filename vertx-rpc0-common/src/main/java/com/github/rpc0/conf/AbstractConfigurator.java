package com.github.rpc0.conf;

import com.esotericsoftware.kryo.util.IntMap;
import com.github.rpc0.annotation.TrustedType;
import com.github.rpc0.kryo.KryoRegistry;
import com.github.rpc0.kryo.TrustedTypeKryoRegistry;
import com.google.common.base.Preconditions;
import com.google.common.reflect.ClassPath;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author fishzhao
 * @since 2022-01-04
 */
@Slf4j
@SuppressWarnings({"UnusedReturnValue"})
public abstract class AbstractConfigurator<T extends AbstractConfigurator<T>> {

  @Getter(AccessLevel.PROTECTED)
  private final ClassLoader classLoader;

  private final IntMap<Class<?>> typeRegistry = new IntMap<>();

  private KryoRegistry kryoRegistry;

  protected AbstractConfigurator(@NonNull ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public final T registerType(@NonNull Class<?> type, int typeId) {
    Preconditions.checkArgument(
            !type.isLocalClass()
            && !type.isPrimitive()
            && !type.isInterface()
            && !type.isArray()
            && !type.isAnnotation()
            && !type.isAnonymousClass()
            && !type.isSynthetic(),
            "Unsupported type: %s",
            type.getTypeName()
    );
    Preconditions.checkArgument(typeId >= 0, "typeId should >= 0: %s, %s", type, typeId);
    Class<?> old = typeRegistry.put(typeId, type);
    Preconditions.checkArgument(
            old == null || old.equals(type),
            "Duplicated typeId for \"%s\" and \"%s\": %s",
            old, type, typeId
    );
    log.info("Register bean class: {} -> {}", typeId, type.getTypeName());
    return self();
  }

  public final T registerType(@NonNull Class<?> type) {
    Preconditions.checkArgument(type.isAnnotationPresent(TrustedType.class), "@%s is required for type %s!",
            TrustedType.class.getSimpleName(), type);
    return registerType(type, type.getAnnotation(TrustedType.class).typeId());
  }

  public final T registerTypes(@NonNull String packageName, boolean recursive) {
    visitPackage(packageName, recursive, type -> {
      if (type.isAnnotationPresent(TrustedType.class)) {
        registerType(type);
      }
    });
    return self();
  }

  @SuppressWarnings("unchecked")
  private T self() {
    return (T) this;
  }

  @SuppressWarnings("UnstableApiUsage")
  protected void visitPackage(@NonNull String packageName,
                              boolean recursive,
                              @NonNull Consumer<? super Class<?>> typeHandler) {
    Set<ClassPath.ClassInfo> classInfoSet;
    try {
      if (recursive) {
        classInfoSet = ClassPath.from(classLoader)
                .getTopLevelClassesRecursive(packageName);
      } else {
        classInfoSet = ClassPath.from(classLoader)
                .getTopLevelClasses(packageName);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (ClassPath.ClassInfo classInfo : classInfoSet) {
      Class<?> topLevel = classInfo.load();
      typeHandler.accept(topLevel);
      for (Class<?> inner : topLevel.getDeclaredClasses()) {
        typeHandler.accept(inner);
      }
    }
  }

  protected final KryoRegistry getKryoRegistry() {
    return kryoRegistry == null ? new TrustedTypeKryoRegistry(typeRegistry) : kryoRegistry;
  }

  public final T setKryoRegistry(KryoRegistry kryoRegistry) {
    if (kryoRegistry != null && !typeRegistry.isEmpty()) {
      log.warn("Use custom kryo registry, preregistered types disposed： {}", typeRegistry);
      typeRegistry.clear();
    }
    this.kryoRegistry = kryoRegistry;
    return self();
  }
}
