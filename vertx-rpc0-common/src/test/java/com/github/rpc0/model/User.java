package com.github.rpc0.model;


import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author fishzhao
 * @since 2022-01-17
 */
@Getter
@Setter
public class User implements Cloneable {

  private long id;
  private String name;
  private OffsetDateTime createTime;
  private List<String> tags;
  private int[] ints;
  private Map<String, Object> attributes;
  private Object[] objects;

  public static User generate() {
    User user = new User();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    user.setId(random.nextLong());
    user.setName(UUID.randomUUID().toString());
    user.setCreateTime(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
    user.setTags(ImmutableList.of(
            randomAscii(random, 16),
            randomAscii(random, 16),
            randomAscii(random, 16)
    ));
    user.setInts(random.ints(16).toArray());
    user.setAttributes(ImmutableMap.of(
            "k1", randomAscii(random, 10),
            "k2", random.nextLong(),
            "k3", random.nextDouble(),
            "k4", randomAscii(random, 128).getBytes(StandardCharsets.UTF_8),
            "k5", randomBitSet(random, 16)
    ));
    user.setObjects(
            new Object[]{randomAscii(random, 32), random.nextFloat(), randomBitSet(random, 25), List.of()}
    );

    return user;
  }

  private static String randomAscii(ThreadLocalRandom random, int len) {
    int[] a = random.ints(len, 48, 123).toArray();
    char[] chars = new char[a.length];
    for (int i = 0; i < chars.length; ++i) {
      chars[i] = (char) a[i];
    }
    return new String(chars);
  }

  private static BitSet randomBitSet(ThreadLocalRandom random, int len) {
    BitSet bitSet = new BitSet(len);
    int[] ints = random.ints(len).toArray();
    for (int i = 0; i < ints.length; i++) {
      bitSet.set(i, (ints[i] & 1) == 1);
    }
    return bitSet;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
            .omitNullValues()
            .add("id", id)
            .add("name", name)
            .add("createTime", createTime)
            .add("tags", tags)
            .add("ints", ints)
            .add("attributes", attributes)
            .add("objects", objects)
            .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return id == user.id && Objects.equals(name, user.name) && Objects.equals(createTime, user.createTime) && Objects.equals(tags, user.tags) && Arrays.equals(ints, user.ints) && Objects.equals(attributes, user.attributes) && Arrays.equals(objects, user.objects);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, name, createTime, tags, attributes);
    result = 31 * result + Arrays.hashCode(ints);
    result = 31 * result + Arrays.hashCode(objects);
    return result;
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}

