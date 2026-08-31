package com.ismsp.chatbot.controller;

import java.util.List;

import com.ismsp.chatbot.dto.FilerDisclosureDto;
import com.ismsp.chatbot.dto.RelatedCompanyDto;
import com.ismsp.chatbot.service.DisclosureGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지분공시 그래프(제출인-공시-기업) 탐색용 API. 벡터 검색으로는 안 되는 멀티홉 질의
 * (예: "이 사람이 지분을 공시한 다른 회사는?")를 위한 것.
 */
@RestController
@RequestMapping("/api/admin/graph")
@RequiredArgsConstructor
public class DisclosureGraphController {

    private final DisclosureGraphService disclosureGraphService;

    @GetMapping("/filers")
    public List<FilerDisclosureDto> filers(
            @RequestParam("corpCode") String corpCode,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return disclosureGraphService.findFilers(corpCode, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/related-companies")
    public List<RelatedCompanyDto> relatedCompanies(
            @RequestParam("filerName") String filerName,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        return disclosureGraphService.findRelatedCompanies(filerName, Math.min(Math.max(limit, 1), 100));
    }
}
