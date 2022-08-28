package study.querydsl.repository;

import study.querydsl.dto.MemberDto;

import java.util.List;

public interface MemberRepositoryCustom {

    List<MemberDto> serch(MemberSearchCondition condition);
}
