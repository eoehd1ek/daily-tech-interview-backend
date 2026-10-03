INSERT INTO question (title, content)
VALUES ('자기 소개', '인사 후, 자신의 이름, 성별을 소개해주세요.');

INSERT INTO evaluation_criterion (question_id, content, max_score, display_order)
SELECT id, '인사', 50, 1 FROM question WHERE title = '자기 소개';

INSERT INTO evaluation_criterion (question_id, content, max_score, display_order)
SELECT id, '본인 이름', 30, 2 FROM question WHERE title = '자기 소개';

INSERT INTO evaluation_criterion (question_id, content, max_score, display_order)
SELECT id, '본인 성별', 20, 3 FROM question WHERE title = '자기 소개';
