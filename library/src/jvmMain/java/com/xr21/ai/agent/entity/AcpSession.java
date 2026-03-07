/*
 * Copyright © 2026 XR21 Team. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xr21.ai.agent.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * ACP 会话状态
 *
 * @author Endless
 */
@Data
public class AcpSession {
    public final List<String> history;
    public final String threadId;
    final String sessionId;
    final String cwd;

    public AcpSession(String sessionId, String threadId, String cwd) {
        this.sessionId = sessionId;
        this.threadId = threadId;
        this.cwd = cwd;
        this.history = new ArrayList<>();
    }


}