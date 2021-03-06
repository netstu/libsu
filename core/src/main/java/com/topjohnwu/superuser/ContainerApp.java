/*
 * Copyright 2018 John "topjohnwu" Wu
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

package com.topjohnwu.superuser;

import android.app.Application;

import androidx.annotation.Nullable;

/**
 * A subclass of {@link Application} with a {@link Shell.Container} injected.
 */
public class ContainerApp extends Application implements Shell.Container {

    private volatile Shell mShell;

    public ContainerApp() {
        Shell.Config.setContainer(this);
    }

    @Nullable
    @Override
    public Shell getShell() {
        return mShell;
    }

    @Override
    public void setShell(@Nullable Shell shell) {
        mShell = shell;
    }
}
