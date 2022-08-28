package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

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

    //페이징
    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
    }

    //집합
    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory //데이터 타입이 여러개여서 tuple을 사용
                .select(member.count(), //member가 총 몇명인지
                        member.age.sum(), //나이 합
                        member.age.avg(),//나이 평균
                        member.age.max(),//나이 최댓값
                        member.age.min() //나이 최솟값
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);
        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 팀 A에 소속된 모든 회원
     */
    @Test
    public void join() throws Exception {
        QMember member = QMember.member;
        QTeam team = QTeam.team;

        List<Member> result = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();


        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    @PersistenceContext
    EntityManagerFactory emf;

    /**
     * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID=t.id and
     * t.name='teamA'
     */
    @Test
    public void join_on_filtering() throws Exception {

        List<Tuple> result = queryFactory //select 한 타입이 여러종류라서 Tuple로 반환된다.
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();


        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
        //결과

        // t=[Member(id=3, username=member1, age=10), Team(id=1, name=teamA)]
        //t=[Member(id=4, username=member2, age=20), Team(id=1, name=teamA)]
        //t=[Member(id=5, username=member3, age=30), null]
        //t=[Member(id=6, username=member4, age=40), null]
    }

    /**
     * 2. 연관관계 없는 엔티티 외부 조인
     * 예) 회원의 이름과 팀의 이름이 같은 대상 외부 조인
     * JPQL: SELECT m, t FROM Member m LEFT JOIN Team t on m.username = t.name
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.username = t.name
     */
    @Test
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) //id(FK)로 매칭되지 않고 on절의 조건으로 걸러짐
                .fetch();


        for (Tuple tuple : result) {
            System.out.println("t=" + tuple);
        }
    }

    /**
     * 세타 조인(연관관계가 없는 필드로 조인) -> 연관관계가 없어도 조인이 가능하다.
     * 회원의 이름이 팀 이름과 같은 회원 조회 (억지성으로 예시)
     */
    @Test
    public void theta_join() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));


        List<Member> result = queryFactory
                .select(member)
                .from(member, team) //member테이블과 team테이블을 모두 조인하고 검색 -> DB에서 최적하는 하면서 진행한다
                .where(member.username.eq(team.name))
                .fetch();


        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")) //Lazy로 걸려있어서 Member객체의 team필드는 데이터를 가져오지 않음
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());

        assertThat(loaded).as("페치조인 미적용").isFalse();

    }

    /**
     * 페치 조인 적용
     *
     * @throws Exception
     */
    @Test
    public void fetchJoinUse() throws Exception {

        em.flush();
        em.clear();

        Member member1 = queryFactory.selectFrom(member)
                .join(member.team, team).fetchJoin() //페치조인을 적용하는 곳 -> join(), leftjoin() 기능 뒤에 fetchJoin()이라고 추가하면 된다.
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member1.getTeam());
        assertThat(loaded).as("페치조인 적용").isTrue();
    }

    /**
     * 나이가 가장 많은 회원 조회
     */
    @Test
    public void subQuery() throws Exception {
        QMember memberSub = new QMember("memberSub"); //alias가 겹치면 안되서 따로 지정

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions.select(memberSub.age.max())
                                .from(memberSub)
                )).fetch();

        assertThat(result).extracting("age").containsExactly(40);


    }

    /**
     * 나이가 평균나이 이상인 회원 조회
     */
    @Test
    public void subQueryGoe() throws Exception {
        QMember memberSub = new QMember("memberSub"); //alias가 겹치면 안되서 따로 지정

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions.select(memberSub.age.avg())
                                .from(memberSub)
                )).fetch();

        assertThat(result).extracting("age").containsExactly(30, 40);


    }


    /**
     * 나이가 평균나이 이상인 회원 조회
     */
    @Test
    public void subQueryIn() throws Exception {
        QMember memberSub = new QMember("memberSub"); //alias가 겹치면 안되서 따로 지정

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.in(
                        JPAExpressions.select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                )).fetch();

        assertThat(result).extracting("age").containsExactly(20, 30, 40);

    }

    @Test
    public void caseQuery() throws Exception {

        List<String> result = queryFactory.select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
    }

    @Test
    public void caseComplexQuery() throws Exception {

        queryFactory.select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

    }

    @Test
    public void enumPrint() {

        queryFactory.select(member.username, Expressions.constant("A")) //상수 A를 함께 출력
                .from(member).fetchFirst();
    }

    @Test
    public void concatWord() {

        queryFactory.select(member.username.concat("_")
                        .concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();
    }

    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)  //하나 이상의 타입을 조회할 때 주로 튜플, DTO를 사용한다. Repository 레벨까지만 사용하도록! -> 튜플을 querydsl 종속적이다.
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username); //Tuple에서 값 조회하는 법
            Integer integer = tuple.get(member.age);

        }
    }


    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class //querydsl의 Projections  이용하여 타입이 다른 데이터 조회가능!
                        , member.username
                        , member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    @Test
    public void findDtoByField() {  //Projections의 field사용
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class //querydsl의 Projections  이용하여 타입이 다른 데이터 조회가능!
                        , member.username
                        , member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }


    @Test
    public void findDtoByConstructor() {  //Projections의 field사용
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class //querydsl의 Projections  이용하여 타입이 다른 데이터 조회가능!
                        , member.username
                        , member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    @Test
    public void findDtoByQueryProjection() { //DTO에서 @QueryProjection을 사요하면 가능

        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();
    }

    @Test
    public void dynamicQuery_BooleanBuilder() {
        //검색조건
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameParam, Integer ageParam) {

        BooleanBuilder builder = new BooleanBuilder();  //검색 조건이 동적일 때 BooleanBuilder를 사용한다.

        if (usernameParam != null) {
            builder.and(member.username.eq(usernameParam));
        }

        if (ageParam != null) {
            builder.and(member.age.eq(ageParam));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();

    }

    @Test
    public void dynamicQuery_WhereBuilder() {
        //검색조건
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameParam, Integer ageParam) {

        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameParam), ageEq(ageParam))
                .fetch();

    }

    private BooleanExpression usernameEq(String usernameParam) {
        return usernameParam != null ? member.username.eq(usernameParam) : null;
    }

    private BooleanExpression ageEq(Integer ageParam) {
        return ageParam != null ? member.age.eq(ageParam) : null;
    }


    @Test
    public void bulkUpdate() {

        //벌크연산은 영속성 컨텍스트를 무시하고 바로 DB로 값을 바꿔버림 -> 영속성컨텍스트와 DB의 값이 다름
        //DB에서 다시 값을 들고와도 영속성컨텍스트에 이미 값이 있으면 영속성 컨텍스트가 우선권을 가지기 때문에 값을 바꾸지 않음 -> 영속성 컨텍스트를 날려버려야한다. em.flush(), em.clear()
        //나이가 28이하인 멤버들 이름을 "비회원"으로 바꾸기
        //리턴 값은 영향을 받은 member의 수
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();
    }


}
