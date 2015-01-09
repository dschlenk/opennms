package org.opennms.features.gwt.graph.resource.list.client;

import org.opennms.features.gwt.graph.resource.list.client.presenter.CustomReportSelectListPresenter;
import org.opennms.features.gwt.graph.resource.list.client.view.ReportSelectListViewImpl;
import org.opennms.features.gwt.graph.resource.list.client.view.ResourceListItem;
import org.opennms.features.gwt.graph.resource.list.client.view.SearchPopup;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.HasWidgets;

public class CustomReportSelectListAppController extends
        ReportSelectListAppController {
    private String m_endUrl;
    
    public CustomReportSelectListAppController(
            JsArray<ResourceListItem> resourceListData, String baseUrl, String endUrl) {
        super(resourceListData, baseUrl);
        m_endUrl = endUrl;
    }
    
    @Override
    public void go(HasWidgets container) {
        new CustomReportSelectListPresenter(new ReportSelectListViewImpl(getResourceList()), new SearchPopup(), getBaseUrl(), m_endUrl).go(container);
    }
    
    
}
