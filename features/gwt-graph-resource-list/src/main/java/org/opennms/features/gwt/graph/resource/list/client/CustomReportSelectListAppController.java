package org.opennms.features.gwt.graph.resource.list.client;

import java.util.ArrayList;
import java.util.List;

import org.opennms.features.gwt.graph.resource.list.client.presenter.CustomReportSelectListPresenter;
import org.opennms.features.gwt.graph.resource.list.client.presenter.Presenter;
import org.opennms.features.gwt.graph.resource.list.client.view.CustomReportSelectListViewImpl;
import org.opennms.features.gwt.graph.resource.list.client.view.ResourceListItem;
import org.opennms.features.gwt.graph.resource.list.client.view.SearchPopup;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.HasWidgets;

public class CustomReportSelectListAppController implements Presenter {

    private List<ResourceListItem> m_resourceList;
    private String m_baseUrl;
    private String m_endUrl;
    
    public CustomReportSelectListAppController(
            JsArray<ResourceListItem> resourceListData, String baseUrl, String endUrl) {
        m_resourceList = convertJsArrayToList(resourceListData);
        m_baseUrl = baseUrl;
        m_endUrl = endUrl;
    }
    
    @Override
    public void go(HasWidgets container) {
        new CustomReportSelectListPresenter(new CustomReportSelectListViewImpl(m_resourceList), new SearchPopup(), m_baseUrl, m_endUrl).go(container);
    }
    
    private List<ResourceListItem> convertJsArrayToList(JsArray<ResourceListItem> resourceList) {
        List<ResourceListItem> data = new ArrayList<ResourceListItem>();
        if (resourceList != null) {
            for(int i = 0; i < resourceList.length(); i++) {
                data.add(resourceList.get(i));
            }
        }
        return data;
    }
}
