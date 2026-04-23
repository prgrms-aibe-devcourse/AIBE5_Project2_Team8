package com.knoc.github.dto;

public record GithubPrFile(
        String filename,
        String status,
        int additions,
        int deletions,
        String patch
) {}