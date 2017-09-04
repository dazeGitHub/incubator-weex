/**
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
package com.taobao.weex.ui.component.binding;

import android.support.v4.util.ArrayMap;
import android.text.TextUtils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.taobao.weex.dom.WXAttr;
import com.taobao.weex.dom.WXDomObject;
import com.taobao.weex.dom.WXEvent;
import com.taobao.weex.dom.binding.BindingUtils;
import com.taobao.weex.dom.binding.WXStatement;
import com.taobao.weex.el.parse.ArrayStack;
import com.taobao.weex.el.parse.Block;
import com.taobao.weex.el.parse.Operators;
import com.taobao.weex.ui.component.WXComponent;
import com.taobao.weex.ui.component.WXComponentFactory;
import com.taobao.weex.ui.component.WXVContainer;
import com.taobao.weex.utils.WXLogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by jianbai.gbj on 2017/8/17.
 * simple statement execute, render component for template list
 */
public class Statements {
    /**
     * recursive copy component,
     * */
    public static WXComponent copyComponentTree(WXComponent component){
        WXComponent copy =  copyComponentTree(component, component.getParent());
        return copy;
    }

    /**
     * recursive copy component,
     * */
    private static final WXComponent copyComponentTree(WXComponent source, WXVContainer parent){
        WXDomObject node = (WXDomObject) source.getDomObject();
        WXComponent component = WXComponentFactory.newInstance(source.getInstance(), node, parent);
        if(source instanceof WXVContainer){
            WXVContainer container = (WXVContainer) source;
            WXVContainer childParent = (WXVContainer) component;
            WXDomObject childParentNode = (WXDomObject) childParent.getDomObject();
            int count = container.getChildCount();
            for (int i = 0; i < count; ++i) {
                WXComponent child = container.getChild(i);
                if (child != null) {
                    WXComponent targetChild = copyComponentTree(child,  childParent);
                    childParent.addChild(targetChild);
                    childParentNode.add((WXDomObject) targetChild.getDomObject(), -1);
                }
            }
        }
        return  component;
    }

    /**
     *  @param component component with v-for statement, v-if statement and bind attrs
     *  @param stack  execute context
     *  render component in context, the function do the following  work.
     *  execute component's v-for statement, v-if statement in context,
     *  and rebuild component's tree with the statement, v-for reuse component execute by pre render.
     *  if executed, component will be removed, don't remove, just mark it waste;
     *  may be next render it can be used.
     *  after statement has executed, render component's binding attrs in context and bind it to component.
     * */
    public static final void doRender(WXComponent component, ArrayStack stack){
        try{
            doRenderComponent(component, stack);
        }catch (Exception e){
            WXLogUtils.e("WeexStatementRender", e);
        }
    }


    /**
     *  @param component component with v-for statement, v-if statement and bind attrs
     *  @param context   execute context
     *  render component in context, the function do the following  work.
     *  execute component's v-for statement, v-if statement in context,
     *  and rebuild component's tree with the statement, v-for reuse component execute by pre render.
     *  if executed, component will be removed, don't remove, just mark it waste;
     *  may be next render it can be used.
     *  after statement has executed, render component's binding attrs in context and bind it to component.
     * */
    static final int doRenderComponent(WXComponent component, ArrayStack context){
        WXVContainer parent = component.getParent();
        WXDomObject domObject = (WXDomObject) component.getDomObject();
        WXAttr attrs = domObject.getAttrs();
        WXStatement statement =  attrs.getStatement();
        if(statement != null){
            WXDomObject parentDomObject = (WXDomObject) parent.getDomObject();
            JSONObject vfor = (JSONObject) statement.get(WXStatement.WX_FOR);
            Block vif = (Block) statement.get(WXStatement.WX_IF);
            int renderIndex = parent.indexOf(component);
            // execute v-for content
            if(vfor != null){
                Block listBlock = (Block) vfor.get(WXStatement.WX_FOR_LIST);
                String indexKey = vfor.getString(WXStatement.WX_FOR_INDEX);
                String itemKey = vfor.getString(WXStatement.WX_FOR_ITEM);
                Object data = null;
                if(listBlock != null) {
                    data = listBlock.execute(context);
                }
                if(data instanceof List
                        && !TextUtils.isEmpty(indexKey)
                        && !TextUtils.isEmpty(itemKey)){
                    List list = (List) data;
                    Map<String, Object> loop = new HashMap<>();
                    context.push(loop);
                    for(int i=0; i<list.size(); i++){
                        loop.put(indexKey, i);
                        loop.put(itemKey, list.get(i));
                        if(vif != null){
                            if(!Operators.isTrue(vif.execute(context))){
                                continue;
                            }
                        }
                        //find resuable renderNode
                        WXComponent renderNode = null;
                        if(renderIndex < parent.getChildCount()){
                            renderNode = parent.getChild(renderIndex);
                            //check is same statment, if true, it is usabled.
                            if(!isCreateFromNodeStatement(renderNode, component)){
                                renderNode = null;
                            }
                        }
                        //none resuable render node, create node, add to parent, but clear node's statement
                        if(renderNode == null){
                            renderNode = copyComponentTree(component, parent);
                            WXDomObject renderNodeDomObject = (WXDomObject) renderNode.getDomObject();
                            renderNodeDomObject.getAttrs().setStatement(null); // clear node's statement
                            parentDomObject.add(renderNodeDomObject, renderIndex);
                            parent.addChild(renderNode, renderIndex);
                            parent.createChildViewAt(renderIndex);
                            renderNode.applyLayoutAndEvent(renderNode);
                            renderNode.bindData(renderNode);
                        }
                        doRenderBindingAttrs(renderNode, domObject, context);
                        doRenderChildNode(renderNode, context);
                        renderIndex++;
                    }
                    context.pop();
                }
                //after v-for execute, remove component created pre v-for.
                for(;renderIndex<parent.getChildCount(); renderIndex++){
                    WXComponent wasteNode = parent.getChild(renderIndex);
                    if(!isCreateFromNodeStatement(wasteNode, component)){
                        break;
                    }
                    wasteNode.setWaste(true);
                }
                return renderIndex - parent.indexOf(component);
            }

            //execute v-if context
            if(vif != null){
                if(!Operators.isTrue(vif.execute(context))){
                    component.setWaste(true);
                    return 1;
                }
            }
        }
        doRenderBindingAttrs(component, domObject, context);
        doRenderChildNode(component, context);
        return  1;
    }

    /**
     * bind attrs and doRenderComponent next
     * */
    private static void doRenderChildNode(WXComponent component, ArrayStack context){
        if(component instanceof WXVContainer){
            WXVContainer container = (WXVContainer) component;
            for(int k=0; k<container.getChildCount();){
                WXComponent next = container.getChild(k);
                k += doRenderComponent(next, context);
            }
        }
    }


    /**
     * check whether render node is create from component node statement.
     * */
    private static boolean isCreateFromNodeStatement(WXComponent renderNode, WXComponent component){
        return (renderNode.getRef() != null && renderNode.getRef().equals(component.getRef()));
    }


    /**
     * render dynamic binding attrs and bind them to component node.
     * */
    private static void doRenderBindingAttrs(WXComponent component, WXDomObject domObject, ArrayStack context){
        component.setWaste(false);
        if(domObject.getAttrs() != null
                && domObject.getAttrs().getBindingAttrs() != null
                && domObject.getAttrs().getBindingAttrs().size() > 0){
            ArrayMap<String, Object> bindAttrs = domObject.getAttrs().getBindingAttrs();
            Map<String, Object> dynamic =  getBindingAttrs(bindAttrs, context);
            domObject.updateAttr(dynamic);
            component.updateProperties(dynamic);
        }
        WXEvent event = domObject.getEvents();
        if(event == null || event.getEventBindingArgs() == null){
            return;
        }
        Set<Map.Entry<String, Object>> eventBindArgsEntrySet = event.getEventBindingArgs().entrySet();
        for(Map.Entry<String, Object> eventBindArgsEntry : eventBindArgsEntrySet){
             List<Object> values = getBindingEventArgs(context, eventBindArgsEntry.getValue());
             if(values != null){
                 event.putEventBindingArgsValue(eventBindArgsEntry.getKey(), values);
             }
        }
    }


    /**
     * @param  bindAttrs  none null,
     * @param  context  context
     * return binding attrs rended value in context
     * */
    public static Map<String, Object> getBindingAttrs(ArrayMap bindAttrs, ArrayStack context){
        Set<Map.Entry<String, Object>> entrySet = bindAttrs.entrySet();
        Map<String, Object> dynamic = new HashMap<>();
        for(Map.Entry<String, Object> entry : entrySet){
            Object binding = entry.getValue();
            String key = entry.getKey();
            if(entry.getValue() instanceof  JSONObject
                    && (((JSONObject) binding).get(BindingUtils.BINDING)  instanceof  Block)){
                Block block = (Block) (((JSONObject) binding).get(BindingUtils.BINDING));
                dynamic.put(key, block.execute(context));
            }else if(binding instanceof JSONArray){
                JSONArray array = (JSONArray) binding;
                StringBuilder builder = new StringBuilder();
                for(int i=0; i<array.size(); i++){
                    Object value = array.get(i);
                    if(value instanceof  CharSequence){
                        builder.append(value);
                        continue;
                    }
                    if(value instanceof JSONObject
                            && (((JSONObject) value).get(BindingUtils.BINDING) instanceof Block)){
                        Block block = (Block) (((JSONObject) value).get(BindingUtils.BINDING));
                        Object blockValue = block.execute(context);
                        if(blockValue == null){
                            blockValue = "";
                        }
                        builder.append(blockValue);
                    }
                }
                dynamic.put(key, builder.toString());
            }
        }
        return  dynamic;
    }

    public static List<Object> getBindingEventArgs(ArrayStack context, Object bindings){
          List<Object>  params = new ArrayList<>(4);
          if(bindings instanceof  JSONArray){
              JSONArray array = (JSONArray) bindings;
              for(int i=0; i<array.size(); i++){
                  Object value = array.get(i);
                  if(value instanceof  JSONObject
                          && (((JSONObject) value).get(BindingUtils.BINDING) instanceof  Block)){
                      Block block = (Block)(((JSONObject) value).get(BindingUtils.BINDING));
                      Object blockValue = block.execute(context);
                      params.add(blockValue);
                  }else{
                      params.add(value);
                  }
              }
          }else if(bindings instanceof  JSONObject){
              JSONObject binding = (JSONObject) bindings;
               if(binding.get(BindingUtils.BINDING) instanceof  Block){
                   Block block = (Block) binding.get(BindingUtils.BINDING);
                   Object blockValue = block.execute(context);
                   params.add(blockValue);
               }else{
                   params.add(bindings.toString());
               }
          }else{
              params.add(bindings.toString());
          }
          return  params;
    }
}
