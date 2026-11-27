package itgeo.dao;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// DataUpdateService 에서만 부른다. 메서드명 = mapper-dataupdate.xml 의 id
@Component
public class DataUpdateDAO {

	@Autowired
	SqlSession session;

	// building 테이블을 만든 시각 (배치의 DB 반영은 새 테이블로 바꿔 끼우므로 = 마지막 전체 반영 시각). "yyyy-MM-dd HH:mm:ss", 없으면 null
	public String getBuildingLoadedAt() {
		return session.selectOne("mapper-dataupdate.getBuildingLoadedAt");
	}
}
