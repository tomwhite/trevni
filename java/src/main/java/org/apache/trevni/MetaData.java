/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.trevni;

import java.io.IOException;
import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Map;
import java.util.LinkedHashMap;

/** */
public class MetaData extends LinkedHashMap<String,byte[]> {

  static final String RESERVED_KEY_PREFIX = "trevni.";

  /** Return the value of a metadata property as a String. */
  public String getString(String key) {
    byte[] value = get(key);
    if (value == null) {
      return null;
    }
    try {
      return new String(value, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /** Return the value of a metadata property as a long. */
  public long getLong(String key) {
    return Long.parseLong(getString(key));
  }

  /** Set a metadata property to a binary value. */
  public MetaData set(String key, byte[] value) {
    if (isReserved(key)) {
      throw new TrevniRuntimeException("Cannot set reserved key: " + key);
    }
    put(key, value);
    return this;
  }
  
  /** Test if a metadata key is reserved. */
  public static boolean isReserved(String key) {
    return key.startsWith(RESERVED_KEY_PREFIX);
  }

  /** Set a metadata property to a String value. */
  public MetaData set(String key, String value) {
    try {
      return set(key, value.getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  /** Set a metadata property to a long value. */
  public MetaData set(String key, long value) {
    return set(key, Long.toString(value));
  }

  void write(OutputBuffer out) {
    out.writeInt(size());
    for (Map.Entry<String,byte[]> e : entrySet()) {
      out.writeString(e.getKey());
      out.writeBytes(e.getValue());
    }
  }

  static void read(InputBuffer in, MetaData metaData) {
    int size = in.readInt();
    for (int i = 0; i < size; i++)
      metaData.put(in.readString(), in.readBytes());
  }
}