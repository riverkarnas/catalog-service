package com.nhcarrigan.catalogservice.dto;

import java.util.List;

public record BulkProductDeleteResponse(List<Long> deleted, List<Long> rejected) {}