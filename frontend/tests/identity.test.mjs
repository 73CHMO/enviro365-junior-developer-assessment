import test from 'node:test';
import assert from 'node:assert/strict';
import { luhn, validIdentity, validCard } from '../identity.js';

test('Luhn accepts a standard test card and rejects a changed check digit', () => {
  assert.equal(luhn('4242424242424242'), true);
  assert.equal(luhn('4242424242424241'), false);
  assert.equal(luhn('0000000000000000'), false);
  assert.equal(luhn('card-number'), false);
});

test('South African identity checks require checksum and matching real birth date', () => {
  assert.equal(validIdentity('8001015009087', '1980-01-01'), true);
  assert.equal(validIdentity('8001015009088', '1980-01-01'), false);
  assert.equal(validIdentity('8001015009087', '1980-01-02'), false);
  assert.equal(validIdentity('8001015009087', '1980-02-31'), false);
});

test('optional card input supports grouping but never accepts invalid digits', () => {
  assert.equal(validCard(''), true);
  assert.equal(validCard('4242 4242 4242 4242'), true);
  assert.equal(validCard('4242-4242-4242-4242'), true);
  assert.equal(validCard('4242424242424241'), false);
  assert.equal(validCard('4242abc4242424242'), false);
});
