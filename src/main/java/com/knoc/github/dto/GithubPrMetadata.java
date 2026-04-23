package com.knoc.github.dto;

import java.util.List;

public record GithubPrMetadata(
        String prUrl,
        String title,
        String state,
        int changedFiles,
        int additions,
        int deletions,
        List<GithubPrFile> files
) {}