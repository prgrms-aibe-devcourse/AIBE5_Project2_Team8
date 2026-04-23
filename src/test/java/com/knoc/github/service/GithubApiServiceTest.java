package com.knoc.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knoc.github.dto.GithubPrFile;
import com.knoc.github.dto.GithubPrMetadata;
import com.knoc.global.exception.BusinessException;
import com.knoc.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GithubApiServiceTest {

    private static final String BASE_URL = "https://api.github.com";
    private static final ObjectMapper om = new ObjectMapper();

    private MockRestServiceServer mockServer;
    private GithubApiService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new GithubApiService(builder.baseUrl(BASE_URL).build());
    }

    // ===== fetchPrMetadata 성공 케이스 =====

    @Test
    @DisplayName("open PR의 메타데이터와 파일 목록을 정상 반환한다")
    void fetchPrMetadata_success() throws Exception {
        enqueuePr("feat: 새 기능", "open", false, 2, 80, 10);
        enqueueFiles(1, List.of(
                fileJson("src/A.java", "modified", 50, 5, "@@ -1 +1 @@\n-old\n+new"),
                fileJson("src/B.java", "added",    30, 0, "@@ -0,0 +1 @@\n+added")
        ));

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/42");

        assertThat(result.title()).isEqualTo("feat: 새 기능");
        assertThat(result.state()).isEqualTo("open");
        assertThat(result.changedFiles()).isEqualTo(2);
        assertThat(result.additions()).isEqualTo(80);
        assertThat(result.deletions()).isEqualTo(10);
        assertThat(result.prUrl()).isEqualTo("https://github.com/owner/repo/pull/42");

        assertThat(result.files()).hasSize(2);
        GithubPrFile first = result.files().get(0);
        assertThat(first.filename()).isEqualTo("src/A.java");
        assertThat(first.status()).isEqualTo("modified");
        assertThat(first.patch()).isEqualTo("@@ -1 +1 @@\n-old\n+new");

        mockServer.verify();
    }

    @Test
    @DisplayName("merged PR은 state를 'merged'로 반환한다")
    void fetchPrMetadata_mergedState() throws Exception {
        enqueuePr("fix: 버그 수정", "closed", true, 1, 5, 3);
        enqueueFiles(1, List.of(fileJson("src/Fix.java", "modified", 5, 3, "diff")));

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/7");

        assertThat(result.state()).isEqualTo("merged");
    }

    @Test
    @DisplayName("closed(미머지) PR은 state를 'closed'로 반환한다")
    void fetchPrMetadata_closedState() throws Exception {
        enqueuePr("WIP: 취소", "closed", false, 1, 2, 0);
        enqueueFiles(1, List.of(fileJson("src/Wip.java", "added", 2, 0, null)));

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/3");

        assertThat(result.state()).isEqualTo("closed");
    }

    // ===== 파일 목록 =====

    @Test
    @DisplayName("binary 파일처럼 patch가 null이면 빈 문자열로 처리한다")
    void fetchPrMetadata_nullPatch_convertedToEmpty() throws Exception {
        enqueuePr("add binary", "open", false, 1, 0, 0);
        enqueueFiles(1, List.of(fileJson("image.png", "added", 0, 0, null)));

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/1");

        assertThat(result.files().get(0).patch()).isEmpty();
    }

    @Test
    @DisplayName("patch가 5000자를 초과하면 5000자로 잘린다")
    void fetchPrMetadata_longPatch_truncated() throws Exception {
        String longPatch = "x".repeat(6_000);
        enqueuePr("large diff", "open", false, 1, 1000, 0);
        enqueueFiles(1, List.of(fileJson("big.java", "modified", 1000, 0, longPatch)));

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/1");

        assertThat(result.files().get(0).patch()).hasSize(5_000);
    }

    @Test
    @DisplayName("파일이 정확히 5000자이면 그대로 반환한다")
    void fetchPrMetadata_patchExact5000_notTruncated() throws Exception {
        String exactPatch = "y".repeat(5_000);
        enqueuePr("exact", "open", false, 1, 500, 0);
        enqueueFiles(1, List.of(fileJson("exact.java", "modified", 500, 0, exactPatch)));

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/1");

        assertThat(result.files().get(0).patch()).hasSize(5_000);
    }

    // ===== 페이지네이션 =====

    @Test
    @DisplayName("파일이 100개 이상이면 다음 페이지까지 모두 가져온다")
    void fetchPrMetadata_pagination() throws Exception {
        enqueuePr("huge PR", "open", false, 110, 500, 100);

        // 1페이지: 100개
        List<Map<String, Object>> page1 = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> fileJson("file" + i + ".java", "modified", 5, 1, "diff" + i))
                .toList();
        enqueueFiles(1, page1);

        // 2페이지: 10개
        List<Map<String, Object>> page2 = IntStream.rangeClosed(101, 110)
                .mapToObj(i -> fileJson("file" + i + ".java", "modified", 5, 1, "diff" + i))
                .toList();
        enqueueFiles(2, page2);

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/1");

        assertThat(result.files()).hasSize(110);
        mockServer.verify();
    }

    @Test
    @DisplayName("파일 목록이 비어 있으면 빈 리스트를 반환한다")
    void fetchPrMetadata_noFiles() throws Exception {
        enqueuePr("empty", "open", false, 0, 0, 0);
        enqueueFiles(1, List.of());

        GithubPrMetadata result = service.fetchPrMetadata(
                "https://github.com/owner/repo/pull/1");

        assertThat(result.files()).isEmpty();
    }

    // ===== URL 파싱 =====

    @Test
    @DisplayName("PR URL 형식이 잘못되면 IllegalArgumentException을 던진다")
    void fetchPrMetadata_invalidUrl() {
        assertThatThrownBy(() ->
                service.fetchPrMetadata("https://github.com/owner/repo/issues/42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 GitHub PR URL 형식이 아닙니다");
    }

    // ===== 오류 처리 =====

    @Test
    @DisplayName("PR 조회 시 404 → GITHUB_PR_NOT_FOUND")
    void fetchPrMetadata_prApi404() {
        mockServer.expect(once(), requestTo(BASE_URL + "/repos/owner/repo/pulls/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() ->
                service.fetchPrMetadata("https://github.com/owner/repo/pull/999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.GITHUB_PR_NOT_FOUND));
    }

    @Test
    @DisplayName("PR 조회 시 500 → EXTERNAL_API_ERROR")
    void fetchPrMetadata_prApi500() {
        mockServer.expect(once(), requestTo(BASE_URL + "/repos/owner/repo/pulls/1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() ->
                service.fetchPrMetadata("https://github.com/owner/repo/pull/1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    @DisplayName("파일 조회 시 404 → GITHUB_PR_NOT_FOUND")
    void fetchPrMetadata_filesApi404() throws Exception {
        enqueuePr("title", "open", false, 1, 10, 0);
        mockServer.expect(requestTo(
                        BASE_URL + "/repos/owner/repo/pulls/1/files?per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() ->
                service.fetchPrMetadata("https://github.com/owner/repo/pull/1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.GITHUB_PR_NOT_FOUND));
    }

    // ===== 헬퍼 =====

    private void enqueuePr(String title, String state, boolean merged,
                           int changedFiles, int additions, int deletions) throws Exception {
        mockServer.expect(request -> {
                    String uri = request.getURI().toString();
                    assertTrue(
                            uri.equals(BASE_URL + "/repos/owner/repo/pulls/42") ||
                                    uri.equals(BASE_URL + "/repos/owner/repo/pulls/7")  ||
                                    uri.equals(BASE_URL + "/repos/owner/repo/pulls/3")  ||
                                    uri.equals(BASE_URL + "/repos/owner/repo/pulls/1")  ||
                                    uri.equals(BASE_URL + "/repos/owner/repo/pulls/999")
                    );
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(om.writeValueAsString(Map.of(
                        "title", title,
                        "state", state,
                        "merged", merged,
                        "changed_files", changedFiles,
                        "additions", additions,
                        "deletions", deletions
                )), MediaType.APPLICATION_JSON));
    }

    private void enqueueFiles(int page, List<Map<String, Object>> files) throws Exception {
        mockServer.expect(requestTo(matchesPattern(
                        BASE_URL + "/repos/owner/repo/pulls/\\d+/files\\?per_page=100&page=" + page)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(om.writeValueAsString(files), MediaType.APPLICATION_JSON));
    }
    private Map<String, Object> fileJson(String filename, String status,
                                         int additions, int deletions, String patch) {
        var map = new java.util.HashMap<String, Object>();
        map.put("filename", filename);
        map.put("status", status);
        map.put("additions", additions);
        map.put("deletions", deletions);
        if (patch != null) map.put("patch", patch);
        return map;
    }
}