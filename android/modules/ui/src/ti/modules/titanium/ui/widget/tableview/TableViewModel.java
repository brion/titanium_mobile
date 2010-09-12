/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.tableview;

import java.util.ArrayList;

import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiDict;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConvert;

import ti.modules.titanium.ui.TableViewProxy;
import ti.modules.titanium.ui.TableViewRowProxy;
import ti.modules.titanium.ui.TableViewSectionProxy;

public class TableViewModel
{
    private static final String LCAT = "TableViewModel";
    private static final boolean DUMP = false;

    // Flat view

    public class Item {
        public Item(int index) {
            this.index = index;
        }
        public boolean hasHeader() {
            return headerText != null;
        }
        
        public int index;
        public int sectionIndex;
        public int indexInSection;
        public String headerText;
        public String footerText;
        public String name;
        public String className;
        public TiViewProxy proxy;
        public Object rowData;
    }

    private TiContext tiContext;
    private TableViewProxy proxy;

    private boolean dirty;

    private ArrayList<Item> viewModel;

    // The unstructured set of data. Modifier operations are treated as edits to this
    // and the section structure.

    public TableViewModel(TiContext tiContext, TableViewProxy proxy) {
        this.tiContext = tiContext;
        this.proxy = proxy;

        viewModel = new ArrayList<Item>();
        dirty = true;
    }
    
    public void release()
    {
    	if (viewModel != null) {
    		viewModel.clear();
    		viewModel = null;
    	}
    	tiContext = null;
    	proxy = null;
    }

    private String classNameForRow(TableViewRowProxy rowProxy) {
        String className = TiConvert.toString(rowProxy.getDynamicValue("className"));
        if (className == null) {
        	className = TableViewProxy.CLASSNAME_DEFAULT;
        }
        return className;
    }

    private Item itemForObject(int index, Object data)
    {
        Item newItem = new Item(index);
        TableViewRowProxy rowProxy = null;

        if (data instanceof TiDict) {
            Object[] args = { data };
            rowProxy = new TableViewRowProxy(tiContext, args);
            rowProxy.setDynamicValue("className", TableViewProxy.CLASSNAME_NORMAL);
            rowProxy.setDynamicValue("rowData", data);
            newItem.proxy = rowProxy;
            newItem.rowData = data;
            newItem.className = TableViewProxy.CLASSNAME_NORMAL;
        } else if (data instanceof TableViewRowProxy) {
            rowProxy = (TableViewRowProxy) data;
            newItem.proxy = rowProxy;
            newItem.rowData = rowProxy;
            String className = TiConvert.toString(rowProxy.getDynamicValue("className"));
            if (className == null) {
            	className = TableViewProxy.CLASSNAME_DEFAULT;
            }
            newItem.className = className;
        } else if (data instanceof TableViewSectionProxy) {
        	newItem.proxy = (TableViewSectionProxy) data;
        } else {
        	throw new IllegalStateException("Un-implemented type: " + (data != null ? data.getClass().getSimpleName() : null));
        }

        return newItem;
    }

    private Item itemForHeader(int index, TableViewSectionProxy proxy, String headerText, String footerText) {
    	Item newItem = new Item(index);
    	newItem.className = TableViewProxy.CLASSNAME_HEADER;
    	if (headerText != null) {
    		newItem.headerText = headerText;
    	} else if (footerText != null) {
    		newItem.footerText = footerText;
    	}
    	newItem.proxy = proxy;

    	return newItem;
    }

    public int getRowCount() {
    	if (viewModel == null) {
    		return 0;
    	}
    	return viewModel.size();
    }

    public TableViewSectionProxy getSection(int index)
    {
    	return proxy.getSections().get(index);
    }
    
    public ArrayList<Item> getViewModel()
    {
        if (dirty) {

            viewModel = new ArrayList<Item>();
            int sectionIndex = 0;
            int indexInSection = 0;
            int index = 0;

            ArrayList<TableViewSectionProxy> sections = proxy.getSections();
            if (sections != null) {

	            for (TableViewSectionProxy section : sections) {
	            	String headerTitle = TiConvert.toString(section.getDynamicValue("headerTitle"));
	            	if (headerTitle != null) {
	            		viewModel.add(itemForHeader(index, section, headerTitle, null));
	            	}
	            	for (TableViewRowProxy row : section.getRows()) {
	            		Item item = new Item(index);
	            		item.sectionIndex = sectionIndex;
	            		item.indexInSection = indexInSection;
	            		item.proxy = row;
	            		item.rowData = row; // TODO capture dictionary?
	            		item.className = classNameForRow(row);

	            		viewModel.add(item);
	            		index++;
	            		indexInSection++;
	            	}

	            	String footerTitle = TiConvert.toString(section.getDynamicValue("footerTitle"));
	            	if (footerTitle != null) {
	            		viewModel.add(itemForHeader(index, section, null, footerTitle));
	            	}

	            	sectionIndex++;
	            	indexInSection = 0;
	            }
	            dirty = false;
	        }
        }
        return viewModel;
    }

    public int getViewIndex(int index) {
        int position = -1;
        // the View index can be larger than model index if there are headers.
        if (viewModel != null && index <= viewModel.size()) {
            for(int i = 0; i < viewModel.size(); i++) {
                Item item = viewModel.get(i);
                if (index == item.index) {
                    position = i;
                    break;
                }
            }
        }

        return position;
    }

    public int getRowHeight(int position, int defaultHeight) {
        int rowHeight = defaultHeight;

        Item item = viewModel.get(position);
        Object rh = item.proxy.getDynamicValue("rowHeight");
        if (rh != null) {
        	rowHeight = TiConvert.toInt(rh);
        }

        return rowHeight;
    }

	public void setDirty() {
		dirty = true;
	}
 }
