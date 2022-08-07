package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @PersistenceContext
    EntityManager em;

    JPAQueryFactory queryFactory = new JPAQueryFactory(em);


    @BeforeEach //테스트 실행 전마다 데이터 셋팅하기
    public void before() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test //jpql 버전
    public void startJPQL() {

        //member1을 찾아라.
        String qlString = "select m from Member m where m.username =:username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void startQuerydsl() {
        JPAQueryFactory queryFactory = new JPAQueryFactory(em);

        QMember m = new QMember("m"); //이름을 구분하는 용도
        //QMember m = QMember.member; -> QMember에서 기본적으로 만들어 놓은 객체를 사용할 수도 있다. (static)

        Member findMember = queryFactory //컴파일시점에 오류를 잡아낼 수 있다 (문법)
                .select(m)
                .from(m)
                .where(m.username.eq("member1")) //파라미터 바인딩 처리
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void search() { //기본 조회
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        , (member.age.between(10, 30)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test //결과조회
    public void resultFetch() {
        List<Member> fetch = queryFactory.selectFrom(member).fetch(); //리스트 조회

        Member fetchOne = queryFactory.selectFrom(QMember.member).fetchOne(); //단건조회

        Member member = queryFactory.selectFrom(QMember.member).fetchFirst();//limit(1).fetch()와 동일

        QueryResults<Member> results = queryFactory.selectFrom(QMember.member).fetchResults();
        List<Member> results1 = results.getResults();
        results.getTotal();//페이징 정보포함, count쿼리 추가 실행

        long count = queryFactory.selectFrom(QMember.member).fetchCount(); //카운트 쿼리만 날라감

    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순(desc)
     * 2. 회원 이름 올림차순(asc)
     * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();


    }
}
