# Cong chay ung dung
server.port=8080

# Ket noi toi database EquineCareDB tren SQL Server
spring.datasource.url=jdbc:sqlserver://103.245.237.252:1433;databaseName=EquineCareDB;encrypt=false;trustServerCertificate=true;
spring.datasource.username=duangua
spring.datasource.password=choig@ycung@nh123
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# Cau hinh JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.SQLServerDialect

# Cau hinh Spring MVC Path Matching Strategy
spring.mvc.pathmatch.matching-strategy=ant_path_matcher

# Cau hinh duong dan Swagger UI kiem thu API
springdoc.swagger-ui.path=/swagger-ui.html

# Cau hinh CORS: dia chi FE duoc phep goi API (nhieu dia chi cach nhau bang dau phay)
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}

# Cau hinh JWT Token (Secret key toi thieu 256 bits cho HS256)
# Access token: 15 phut = 900000 ms; het han thi goi /api/v1/auth/refresh de lay token moi
jwt.secret=${JWT_SECRET:HorseTrackEquineCareSecretKey2026SecureJwtAuthenticationSWP391Group3}
jwt.expiration-ms=${JWT_EXPIRATION_MS:900000}
# Refresh token: 7 ngay = 604800000 ms
jwt.refresh-expiration-ms=${JWT_REFRESH_EXPIRATION_MS:604800000}
