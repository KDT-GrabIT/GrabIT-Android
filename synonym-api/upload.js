/**
 * data/synonyms.json 편집 후 DB 갱신
 * - MONGODB_URI 필요 (.env)
 * - 실행: npm run upload
 */
require('dotenv').config();
const { MongoClient } = require('mongodb');
const path = require('path');
const fs = require('fs');

const DB_NAME = 'grabit';
const COL_ANSWERS = 'answer_synonyms';
const COL_PRODUCTS = 'product_synonyms';

const jsonPath = path.join(__dirname, 'data', 'synonyms.json');

async function run() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('.env 파일에 MONGODB_URI를 넣고 다시 실행하세요.');
    process.exit(1);
  }
  if (!fs.existsSync(jsonPath)) {
    console.error('data/synonyms.json 파일이 없습니다.');
    process.exit(1);
  }
  const raw = fs.readFileSync(jsonPath, 'utf8');
  let data;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    console.error('synonyms.json JSON 파싱 실패:', e.message);
    process.exit(1);
  }
  const answers = data.answers || [];
  const products = data.products || [];

  const client = new MongoClient(uri);
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const answerCol = db.collection(COL_ANSWERS);
    const productCol = db.collection(COL_PRODUCTS);

    await answerCol.deleteMany({});
    if (answers.length > 0) {
      await answerCol.insertMany(answers);
    }
    console.log('answer_synonyms:', answers.length, '건 반영');

    await productCol.deleteMany({});
    if (products.length > 0) {
      await productCol.insertMany(products);
    }
    console.log('product_synonyms:', products.length, '건 반영');

    console.log('DB 갱신 완료.');
  } catch (e) {
    console.error('업로드 실패:', e);
    process.exit(1);
  } finally {
    await client.close();
  }
}

run();
