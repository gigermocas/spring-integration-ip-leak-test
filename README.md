# spring-integration-ip-leak-test
Sample to replicate memory leak in spring-integration-ip

Program will create a tcp connection with port 5000 and will open/close sockets to it 100 times, a heap dump can be genrated and analysed afterwards
