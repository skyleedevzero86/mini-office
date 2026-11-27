package com.sleekydz86.contentmeta.tmdb.infrastructure;

import org.apache.ibatis.annotations.Mapper;

/** TMDB 정보 반영 프로시저 (mapper/tmdb/TmdbMapper.xml) */
@Mapper
public interface TmdbMapper {

    void callTitleTmdbApply(TmdbApplyCall call);
}
