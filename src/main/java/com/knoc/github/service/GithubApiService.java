package com.knoc.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.knoc.github.dto.GithubPrFile;
import com.knoc.github.dto.GithubPrMetadata;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class GithubApiService {

    private static final Pattern PR_URL_PATTERN =
            Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)$");

    private static final int PER_PAGE = 100;
    private static final int MAX_PATCH_LENGTH = 5_000;

    private final RestClient githubRestClient;

    public GithubPrMetadata fetchPrMetadata(String prUrl) {
        Matcher matcher = PR_URL_PATTERN.matcher(prUrl.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("올바른 GitHub PR URL 형식이 아닙니다");
        }

        String owner     = matcher.group(1);
        String repo      = matcher.group(2);
        int pullNumber   = Integer.parseInt(matcher.group(3));

        GithubPrResponse pr = getPr(owner, repo, pullNumber);
        List<GithubPrFile> files = fetchPrFiles(owner, repo, pullNumber);

        String state = pr.merged() ? "merged" : pr.state();

        return new GithubPrMetadata(
                prUrl,
                pr.title(),
                state,
                pr.changedFiles(),
                pr.additions(),
                pr.deletions(),
                files
        );
    }
    //PR의 기본 정보 조회
    private GithubPrResponse getPr(String owner, String repo, int pullNumber) {
        try {
            return githubRestClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{pull_number}", owner, repo, pullNumber)
                    .retrieve()
                    .body(GithubPrResponse.class);
        } catch (HttpClientErrorException e) {
            throw new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND);
        } catch (HttpServerErrorException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
        }
    }

    //PR에서 변경된 파일 목록 조회
    private List<GithubPrFile> fetchPrFiles(String owner, String repo, int pullNumber) {
        List<GithubPrFile> result = new ArrayList<>();
        int page = 1;

        while (true) {
            List<GithubFileResponse> pageFiles;
            try {
                pageFiles = githubRestClient.get()
                        .uri("/repos/{owner}/{repo}/pulls/{pull_number}/files?per_page={per_page}&page={page}",
                                owner, repo, pullNumber, PER_PAGE, page)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<GithubFileResponse>>() {});
            } catch (HttpClientErrorException e) {
                throw new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND);
            } catch (HttpServerErrorException e) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR);
            }

            if (pageFiles == null || pageFiles.isEmpty()) break;

            pageFiles.stream()
                    .map(f -> new GithubPrFile(
                            f.filename(), f.status(), f.additions(), f.deletions(),
                            truncatePatch(f.patch())))
                    .forEach(result::add);

            if (pageFiles.size() < PER_PAGE) break;
            page++;
        }

        return result;
    }

    private static String truncatePatch(String patch) {
        if (patch == null) return "";
        return patch.length() <= MAX_PATCH_LENGTH ? patch : patch.substring(0, MAX_PATCH_LENGTH);
    }

    // ===== GitHub API 응답 전용 내부 레코드 =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubPrResponse(
            String title,
            String state,
            boolean merged,
            @JsonProperty("changed_files") int changedFiles,
            int additions,
            int deletions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubFileResponse(
            String filename,
            String status,
            int additions,
            int deletions,
            String patch
    ) {}
}