package org.opennms.features.gwt.graph.resource.list.client.presenter;

import java.util.List;

import org.opennms.features.gwt.graph.resource.list.client.presenter.DefaultResourceListPresenter.SearchPopupDisplay;
import org.opennms.features.gwt.graph.resource.list.client.view.ReportSelectListView;
import org.opennms.features.gwt.graph.resource.list.client.view.ResourceListItem;

import com.google.gwt.user.client.Window.Location;

public class CustomReportSelectListPresenter extends
        ReportSelectListPresenter {
    
    private String m_endUrl;
    
    public CustomReportSelectListPresenter(
            ReportSelectListView<ResourceListItem> view,
            SearchPopupDisplay searchView, String baseUrl, String endUrl) {
        super(view, searchView, baseUrl);
        m_endUrl = endUrl;
    }
    @Override
    public void onGraphButtonClick() {
        List<ResourceListItem> reports = getView().getSelectedReports();
        if(reports != null) {
            buildUrlAndGoToGraphPage(reports);
        } else {
            getView().showWarning();
        }
    }

    private void buildUrlAndGoToGraphPage(List<ResourceListItem> reports) {
        StringBuilder sb = new StringBuilder();
        sb.append(getBaseUrl() + m_endUrl + "?resourceId=");
        
        boolean first = true;
        for(ResourceListItem item : reports) {
            if(!first) {
                
                sb.append("&resourceId=");
            }
            sb.append(item.getId());
            first = false;
        }
        
        Location.assign(sb.toString());
    }
}
