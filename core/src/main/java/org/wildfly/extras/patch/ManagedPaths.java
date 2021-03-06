/*
 * #%L
 * Fuse Patch :: Core
 * %%
 * Copyright (C) 2015 Private
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wildfly.extras.patch;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.wildfly.extras.patch.Record.Action;
import org.wildfly.extras.patch.utils.IllegalArgumentAssertion;

/**
 * A set of managed server paths. 
 *
 * @author thomas.diesler@jboss.com
 * @since 05-Aug-2015
 */
public final class ManagedPaths {

    private final Map<Path, ManagedPath> managedPaths = new HashMap<>();

    public ManagedPaths(List<ManagedPath> managedPaths) {
        IllegalArgumentAssertion.assertNotNull(managedPaths, "managedPaths");
        for (ManagedPath mpath : managedPaths) {
            this.managedPaths.put(mpath.getPath(), mpath);
        }
    }

    public ManagedPath getManagedPath(Path path) {
        return managedPaths.get(path);
    }

    public List<ManagedPath> getManagedPaths() {
        List<Path> keys = new ArrayList<>(managedPaths.keySet());
        Collections.sort(keys);
        List<ManagedPath> result = new ArrayList<>();
        for (Path path : keys) {
            result.add(managedPaths.get(path));
        }
        return Collections.unmodifiableList(result);
    }

    public ManagedPaths updatePaths(Path rootPath, SmartPatch smartPatch, Action... actions) {
        List<Action> actlist = Arrays.asList(actions);
        for (Record rec : smartPatch.getRecords()) {
            Action act = rec.getAction();
            if (actlist.contains(act)) {
                if (act == Action.ADD) {
                    addPathOwner(rootPath, rec.getPath(), rec.getPatchId());
                } else if (act == Action.UPD) {
                    addPathOwner(rootPath, rec.getPath(), rec.getPatchId());
                } else if (act == Action.DEL) {
                    removePathOwner(rootPath, rec.getPath(), rec.getPatchId());
                }
            }
        }
        return this;
    }

    private void addPathOwner(Path rootPath, Path path, PatchId owner) {
        
        // Recursively add managed parent dirs
        Path parent = path.getParent();
        if (parent != null) {
            File parentDir = rootPath.resolve(parent).toFile();
            if (!parentDir.exists() || managedPaths.get(parent) != null) {
                addPathOwner(rootPath, parent, owner);
            }
        }
        
        ManagedPath mpath = managedPaths.get(path);
        if (mpath == null) {
            List<PatchId> owners = Collections.singletonList(owner);
            File file = rootPath.resolve(path).toFile();
            if (file.isFile()) {
                owners = new ArrayList<>(owners);
                owners.add(0, Server.SERVER_ID);
            }
            mpath = ManagedPath.create(path, owners);
            managedPaths.put(path, mpath);
        } else {
            List<PatchId> owners = new ArrayList<>(mpath.getOwners());
            removeOwner(owners, owner);
            owners.add(owner);
            mpath = ManagedPath.create(mpath.getPath(), owners);
            managedPaths.put(path, mpath);
        }
    }

    private void removePathOwner(Path rootPath, Path path, PatchId owner) {
        
        ManagedPath mpath = managedPaths.get(path);
        if (mpath != null) {
            List<PatchId> owners = new ArrayList<>(mpath.getOwners());
            removeOwner(owners, owner);
            if (owners.size() == 1 && owners.contains(Server.SERVER_ID)) {
                owners.clear();
            }
            if (!owners.isEmpty()) {
                mpath = ManagedPath.create(mpath.getPath(), owners);
                managedPaths.put(mpath.getPath(), mpath);
            } else {
                managedPaths.remove(mpath.getPath());
            }
        }
        
        // Recursively remove managed parent dirs
        Path parent = path.getParent();
        if (parent != null) {
            File file = rootPath.resolve(parent).toFile();
            if (!file.exists()) {
                removePathOwner(rootPath, parent, owner);
            }
        }
    }

    private void removeOwner(List<PatchId> owners, PatchId owner) {
        Iterator<PatchId> it = owners.iterator();
        while (it.hasNext()) {
            PatchId aux = it.next();
            if (aux.getName().equals(owner.getName())) {
                it.remove();
                break;
            }
        }
    }
}