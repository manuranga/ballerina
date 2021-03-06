/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.jvm.values;

import org.ballerinalang.jvm.BallerinaErrors;
import org.ballerinalang.jvm.BallerinaXMLSerializer;
import org.ballerinalang.jvm.XMLNodeType;
import org.ballerinalang.jvm.types.BArrayType;
import org.ballerinalang.jvm.types.BTypes;
import org.ballerinalang.jvm.util.BLangConstants;
import org.ballerinalang.jvm.util.exceptions.BallerinaErrorReasons;
import org.ballerinalang.jvm.util.exceptions.BallerinaException;
import org.ballerinalang.jvm.values.api.BMap;
import org.ballerinalang.jvm.values.api.BString;
import org.ballerinalang.jvm.values.api.BXML;
import org.ballerinalang.jvm.values.freeze.FreezeUtils;
import org.ballerinalang.jvm.values.freeze.State;
import org.ballerinalang.jvm.values.freeze.Status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.ballerinalang.jvm.util.BLangConstants.STRING_EMPTY_VALUE;
import static org.ballerinalang.jvm.util.BLangConstants.STRING_NULL_VALUE;
import static org.ballerinalang.jvm.util.BLangConstants.XML_LANG_LIB;

/**
 * <p>
 * {@code BXMLSequence} represents a sequence of {@link XMLItem}s in Ballerina.
 * </p>
 * <p>
 * <i>Note: This is an internal API and may change in future versions.</i>
 * </p>
 * 
 * @since 0.995.0
 */
public final class XMLSequence extends XMLValue {

    List<BXML> children;

    /**
     * Create an empty xml sequence.
     */
    public XMLSequence() {
        children = new ArrayList<>();
    }

    public XMLSequence(List<BXML> children) {
        this.children = children;
    }

    public XMLSequence(BXML child) {
        this.children = new ArrayList<>();
        this.children.add(child);
    }

    public List<BXML> getChildrenList() {
        return children;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLNodeType getNodeType() {
        return XMLNodeType.SEQUENCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSingleton() {
        return children.size() == 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getItemType() {
        if (isSingleton()) {
            return children.get(0).getItemType();
        }

        return XMLNodeType.SEQUENCE.value();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getElementName() {
        if (isSingleton()) {
            return children.get(0).getElementName();
        }
        return STRING_EMPTY_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTextValue() {
        StringBuilder seqTextBuilder = new StringBuilder();
        for (BXML x : children) {
            if (x.getNodeType() == XMLNodeType.ELEMENT || x.getNodeType() == XMLNodeType.TEXT) {
                seqTextBuilder.append(x.getTextValue());
            }
        }
        return seqTextBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAttribute(String localName, String namespace) {
        if (isSingleton()) {
            return children.get(0).getAttribute(localName, namespace);
        }

        return STRING_NULL_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAttribute(String localName, String namespace, String prefix) {
        if (isSingleton()) {
            return children.get(0).getAttribute(localName, namespace, prefix);
        }

        return STRING_NULL_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttribute(String localName, String namespace, String prefix, String value) {
        if (isSingleton()) {
            children.get(0).setAttribute(localName, namespace, prefix, value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MapValue<String, String> getAttributesMap() {
        if (isSingleton()) {
            return (MapValue<String, String>) children.get(0).getAttributesMap();
        }

        return null;
    }

    @Override
    public void setAttributes(BMap<String, ?> attributes) {
        synchronized (this) {
            if (freezeStatus.getState() != State.UNFROZEN) {
                FreezeUtils.handleInvalidUpdate(freezeStatus.getState(), XML_LANG_LIB);
            }
        }

        if (isSingleton()) {
            children.get(0).setAttributes(attributes);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue elements() {
        List elementsSeq = new ArrayList<XMLValue>();
        for (BXML child : children) {
            if (child.getNodeType() == XMLNodeType.ELEMENT) {
                elementsSeq.add(child);
            }
        }
        return new XMLSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue elements(String qname) {
        List<BXML> elementsSeq = new ArrayList<>();
        String qnameStr = getQname(qname).toString();
        for (BXML child : children) {
            if (child.getElementName().equals(qnameStr)) {
                elementsSeq.add(child);
            }
        }
        return new XMLSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue children() {
        if (children.size() == 1) {
            return (XMLValue) children.get(0).children();
        }
        return new XMLSequence(new ArrayList<>(children));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue children(String qname) {
        List<BXML> selected = new ArrayList<>();
        if (this.children.size() == 1) {
            BXML bxml = this.children.get(0);
            return (XMLValue) bxml.children(qname);
        }

        for (BXML elem : this.children) {
            XMLSequence elements = (XMLSequence) elem.children().elements(qname);
            List<BXML> childrenList = elements.getChildrenList();
            if (childrenList.size() == 1) {
                selected.add(childrenList.get(0));
            } else if (childrenList.size() > 1) {
                selected.addAll(childrenList);
            }
        }

        if (selected.size() == 1) {
            return (XMLValue) selected.get(0);
        }

        return new XMLSequence(selected);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChildren(BXML seq) {
        synchronized (this) {
            if (freezeStatus.getState() != State.UNFROZEN) {
                FreezeUtils.handleInvalidUpdate(freezeStatus.getState(), XML_LANG_LIB);
            }
        }

        if (children.size() != 1) {
            throw BallerinaErrors.createError("not an " + XMLNodeType.ELEMENT);
        }

        children.get(0).setChildren(seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public void addChildren(BXML seq) {
        synchronized (this) {
            if (freezeStatus.getState() != State.UNFROZEN) {
                FreezeUtils.handleInvalidUpdate(freezeStatus.getState(), XML_LANG_LIB);
            }
        }

        if (children.size() != 1) {
            throw BallerinaErrors.createError("not an " + XMLNodeType.ELEMENT);
        }

        children.get(0).addChildren(seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue strip() {
        List<BXML> elementsSeq = new ArrayList<>();
        boolean prevChildWasATextNode = false;
        String prevConsecutiveText = null;

        for (BXML x : children) {
            XMLValue item = (XMLValue) x;
            if (item.getNodeType() == XMLNodeType.TEXT) {
                if (prevChildWasATextNode) {
                    prevConsecutiveText += x.getTextValue();
                } else {
                    prevConsecutiveText = x.getTextValue();
                }
                prevChildWasATextNode = true;
            } else if (item.getNodeType() == XMLNodeType.ELEMENT) {
                // Previous child was a text node and now we see a element node, we need to add last child to the list
                if (prevChildWasATextNode && !prevConsecutiveText.trim().isEmpty()) {
                    elementsSeq.add(new XMLText(prevConsecutiveText));
                    prevConsecutiveText = null;
                }
                prevChildWasATextNode = false;
                elementsSeq.add(x);
            }
        }
        if (prevChildWasATextNode && !prevConsecutiveText.trim().isEmpty()) {
            elementsSeq.add(new XMLText(prevConsecutiveText));
        }

        return new XMLSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue slice(long startIndex, long endIndex) {
        if (startIndex > this.children.size() || endIndex > this.children.size() || startIndex < -1 || endIndex < -1) {
            throw BallerinaErrors.createError("index out of range: [" + startIndex + "," + endIndex + "]");
        }

        if (startIndex == -1) {
            startIndex = 0;
        }

        if (endIndex == -1) {
            endIndex = children.size();
        }

        if (startIndex == endIndex) {
            return new XMLSequence();
        }

        if (startIndex > endIndex) {
            throw BallerinaErrors.createError("invalid indices: " + startIndex + " < " + endIndex);
        }

        int j = 0;
        List<BXML> elementsSeq = new ArrayList<>();
        for (int i = (int) startIndex; i < endIndex; i++) {
            elementsSeq.add(j++, children.get(i));
        }

        return new XMLSequence(elementsSeq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue descendants(String qname) {
        List<BXML> descendants = new ArrayList<>();
        for (BXML x : children) {
            XMLItem element = (XMLItem) x;
        if (element.getQName().toString().equals(getQname(qname).toString())) {
                descendants.add(element);
                continue;
            }
            if (element.getNodeType() == XMLNodeType.ELEMENT) {
                addDescendants(descendants, element, getQname(qname).toString());
            }
        }

        return new XMLSequence(descendants);
    }

    // Methods from Datasource impl

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(OutputStream outputStream) {
        if (outputStream instanceof BallerinaXMLSerializer) {
            ((BallerinaXMLSerializer) outputStream).write(this);
        } else {
            BallerinaXMLSerializer ballerinaXMLSerializer = new BallerinaXMLSerializer(outputStream);
            ballerinaXMLSerializer.write(this);
            try {
                ballerinaXMLSerializer.flush();
                ballerinaXMLSerializer.close();
            } catch (IOException e) {
                throw new BallerinaException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object value() {
        BArrayType bArrayType = new BArrayType(BTypes.typeXML);
        return new ArrayValueImpl(children.toArray(), bArrayType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        try {
            return stringValue();
        } catch (Throwable t) {
            handleXmlException("failed to get xml as string: ", t);
        }
        return BLangConstants.STRING_NULL_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String stringValue() {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BallerinaXMLSerializer serializer = new BallerinaXMLSerializer(outputStream);
            serializer.write(this);
            serializer.flush();
            String str = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            serializer.close();
            return str;
        } catch (Throwable t) {
            handleXmlException("failed to get xml as string: ", t);
        }
        return BLangConstants.STRING_NULL_VALUE;
    }

    @Override
    public BString bStringValue() {
        return null;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Object copy(Map<Object, Object> refs) {
        if (isFrozen()) {
            return this;
        }

        if (refs.containsKey(this)) {
            return refs.get(this);
        }

        ArrayList<BXML> copiedChildrenList = new ArrayList<>(children.size());
        XMLSequence copiedSeq = new XMLSequence(copiedChildrenList);
        refs.put(this, copiedSeq);
        for (BXML child : children) {
            copiedChildrenList.add((XMLValue) child.copy(refs));
        }

        return copiedSeq;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object frozenCopy(Map<Object, Object> refs) {
        XMLSequence copy = (XMLSequence) copy(refs);
        if (!copy.isFrozen()) {
            copy.freezeDirect();
        }
        return copy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XMLValue getItem(int index) {
        try {
            return (XMLValue) this.children.get(index);
        } catch (IndexOutOfBoundsException e) {
            throw BallerinaErrors.createError(BallerinaErrorReasons.XML_OPERATION_ERROR,
                    "IndexOutOfRange " + e.getMessage());
        } catch (Exception e) {
            throw BallerinaErrors.createError(BallerinaErrorReasons.XML_OPERATION_ERROR, e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.children.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void build() {
        for (BXML child : children) {
            child.build();
        }
    }

    @Override
    public void removeAttribute(String qname) {
        synchronized (this) {
            if (freezeStatus.getState() != State.UNFROZEN) {
                FreezeUtils.handleInvalidUpdate(freezeStatus.getState(), XML_LANG_LIB);
            }
        }

        if (children.size() != 1) {
            throw BallerinaErrors.createError("not an " + XMLNodeType.ELEMENT);
        }

        children.get(0).removeAttribute(qname);
    }

    @Override
    @Deprecated
    public void removeChildren(String qname) {
        synchronized (this) {
            if (freezeStatus.getState() != State.UNFROZEN) {
                FreezeUtils.handleInvalidUpdate(freezeStatus.getState(), XML_LANG_LIB);
            }
        }

        if (children.size() != 1) {
            throw BallerinaErrors.createError("not an " + XMLNodeType.ELEMENT);
        }

        children.get(0).removeChildren(qname);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void attemptFreeze(Status freezeStatus) {
        if (FreezeUtils.isOpenForFreeze(this.freezeStatus, freezeStatus)) {
            this.freezeStatus = freezeStatus;
            for (BXML elem : children) {
                elem.attemptFreeze((freezeStatus));
            }
        }
    }

    @Override
    public void freezeDirect() {
        this.freezeStatus.setFrozen();
        for (BXML elem : children) {
            elem.freezeDirect();
        }
    }

    @Override
    public IteratorValue getIterator() {
        return new IteratorValue() {
            @Override
            public BString bStringValue() {
                return null;
            }

            Iterator<BXML> iterator = children.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return iterator.next();
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof XMLSequence) {
            XMLSequence that = (XMLSequence) obj;
            return that.children.equals(this.children);
        }
        if (obj instanceof XMLItem) {
            return this.children.size() == 1 && this.children.get(0).equals(obj);
        }
        return false;
    }
}
