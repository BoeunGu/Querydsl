package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class MemberDto {

    private String username;
    private int age;


    @QueryProjection //DTO로 QMember생성 , 편하지만 순수한 DTO(querydsl에 의존성을 가지기 때문에)가 되지 못하는 아키텍쳐적 단점이 있다.
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
