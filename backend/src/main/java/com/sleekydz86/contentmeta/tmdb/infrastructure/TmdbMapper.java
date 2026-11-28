package com.sleekydz86.contentmeta.tmdb.infrastructure;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TmdbMapper {

    void callTitleTmdbApply(TmdbApplyCall call);
}
