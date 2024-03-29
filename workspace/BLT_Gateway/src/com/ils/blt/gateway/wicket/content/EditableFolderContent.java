/*
 * The code below was based on Wicket Examples - Advanced Tree Page.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ils.blt.gateway.wicket.content;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.Folder;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;

import com.ils.blt.gateway.engine.ProcessNode;

public class EditableFolderContent extends Content {
	private static final long serialVersionUID = 639834109591112523L;

	@Override
    public Component newContentComponent(String id, final AbstractTree<ProcessNode> tree, IModel<ProcessNode> model)
    {
        return new Folder<ProcessNode>(id, tree, model) {
			private static final long serialVersionUID = 1L;

			/**
             * Always clickable.
             */
            @Override
            protected boolean isClickable() {
                return true;
            }

            /**
             * AjaxEditableLabel won't work reliable in Safari if wrapped in a Link, so simply
             * replace the anchor with a &lt;span&gt;.
             */
            @Override
            protected MarkupContainer newLinkComponent(String nid, IModel<ProcessNode> mdl) {
                return new WebMarkupContainer(nid) {
					private static final long serialVersionUID = 1L;

					@Override
                    protected void onComponentTag(ComponentTag tag)
                    {
                        tag.setName("span");
                        super.onComponentTag(tag);
                    }
                };
            }

            @Override
            protected Component newLabelComponent(String nid, final IModel<ProcessNode> mdl) {
                return new Label(nid, new PropertyModel<String>(mdl, "name"));

            }
        };
    }
}