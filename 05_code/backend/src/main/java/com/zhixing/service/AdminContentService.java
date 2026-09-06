package com.zhixing.service;

import com.zhixing.mapper.AdminContentMapper;
import com.zhixing.model.ContentCoverageView;
import org.springframework.stereotype.Service;

@Service
public class AdminContentService {
    private final AdminContentMapper contents;
    public AdminContentService(AdminContentMapper contents){this.contents=contents;}
    public ContentCoverageView coverage(){ContentCoverageView view=new ContentCoverageView();view.setPublishedTotal(contents.countPublished());view.setSourceCount(contents.countSources());view.setTechnicalTopics(contents.selectTechnicalTopics());view.setWordStages(contents.selectWordStages());return view;}
}
