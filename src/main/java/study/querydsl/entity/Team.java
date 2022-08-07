package study.querydsl.entity;


import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name"}) //본인 소유 필드만 지정해야한다. 연관관계 필드는 x -> 서로 toString메소드르 봐라봐서 에러가 남
public class Team {

    @Id
    @GeneratedValue
    @Column(name = "team_id")
    private Long id;
    private String name;


    @OneToMany(mappedBy = "team")
    private List<Member> members = new ArrayList<>();


    public Team(String name) {
        this.name = name;
    }
}
