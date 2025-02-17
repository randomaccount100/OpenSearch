/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.node;

import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Setting.Property;
import org.opensearch.common.unit.ByteSizeValue;
import org.opensearch.indices.recovery.RecoverySettings;
import org.opensearch.plugins.Plugin;

import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Marker plugin that will trigger {@link MockNode} making {@link #CHUNK_SIZE_SETTING} dynamic.
 */
public class RecoverySettingsChunkSizePlugin extends Plugin {
    /**
     * The chunk size. Only exposed by tests.
     */
    public static final Setting<ByteSizeValue> CHUNK_SIZE_SETTING = Setting.byteSizeSetting("indices.recovery.chunk_size",
            RecoverySettings.DEFAULT_CHUNK_SIZE, Property.Dynamic, Property.NodeScope);

    @Override
    public List<Setting<?>> getSettings() {
        return singletonList(CHUNK_SIZE_SETTING);
    }
}
