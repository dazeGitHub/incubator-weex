/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.taobao.weex.ui;

import com.taobao.weex.WXSDKInstance;
import com.taobao.weex.bridge.Invoker;
import com.taobao.weex.ui.component.WXComponent;
import com.taobao.weex.ui.component.WXVContainer;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by sospartan on 8/4/16.
 */
public class ComponentHolderTest {
    public static class TestComponentHolder implements IFComponentHolder{

      WXComponent mComponent;
      TestComponentHolder(WXComponent comp){
        mComponent = comp;
      }

      @Override
      public void loadIfNonLazy() {

      }

      @Override
      public Invoker getPropertyInvoker(String name) {
        return null;
      }

      @Override
      public Invoker getMethodInvoker(String name) {
        return null;
      }

      @Override
      public String[] getMethods() {
        return new String[0];
      }

      @Override
      public WXComponent createInstance(WXSDKInstance instance, WXDomObject node, WXVContainer parent) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        mComponent.bindHolder(this);
        return mComponent;
      }
    }

}
